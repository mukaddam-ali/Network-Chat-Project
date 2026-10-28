// Chat server.
// One program does two jobs on the same port:
//   1. serves the web page (HTTP)
//   2. handles the chat (WebSocket)
//
// Idea: the server keeps a list of groups. Each group has its members.
// When someone sends a message, the server sends it only to the members
// of that group.

const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');

const PORT = 3000;
const PUBLIC_FOLDER = path.join(__dirname, 'public');
const MAX_HISTORY = 100; // how many old messages a new member can see

// ---------- 1. Web server: gives the browser the files in /public ----------

const fileTypes = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript' };

const httpServer = http.createServer(function (req, res) {
  let url = req.url === '/' ? '/index.html' : req.url;
  let filePath = path.join(PUBLIC_FOLDER, path.normalize(url));

  // don't let anyone read files outside the public folder
  if (!filePath.startsWith(PUBLIC_FOLDER)) {
    res.writeHead(403);
    return res.end();
  }

  fs.readFile(filePath, function (err, data) {
    if (err) {
      res.writeHead(404);
      return res.end('Not found');
    }
    res.writeHead(200, { 'Content-Type': fileTypes[path.extname(filePath)] || 'text/plain' });
    res.end(data);
  });
});

// ---------- 2. Chat server (WebSocket) ----------

const wss = new WebSocketServer({ server: httpServer });

// All groups. The key is the group name, the value looks like:
//   { password: '123' or null, members: [sockets...], history: [messages...] }
const groups = {
  General: { password: null, members: [], history: [] },
};

// small helpers
function sendTo(client, data) {
  if (client.readyState === 1) {
    client.send(JSON.stringify(data));
  }
}

function sendToGroup(groupName, data) {
  groups[groupName].members.forEach(function (client) {
    sendTo(client, data);
  });
}

function cleanText(text, maxLength) {
  return String(text || '').replace(/\s+/g, ' ').trim().slice(0, maxLength);
}

// find a group ignoring upper/lower case ("study" finds "Study")
function findGroupName(name) {
  return Object.keys(groups).find(function (g) {
    return g.toLowerCase() === name.toLowerCase();
  });
}

function nameIsTaken(name) {
  let taken = false;
  wss.clients.forEach(function (c) {
    if (c.userName && c.userName.toLowerCase() === name.toLowerCase()) taken = true;
  });
  return taken;
}

// Tell every logged-in user which groups exist (so the list is always up to date)
function sendGroupListToEveryone() {
  const list = Object.keys(groups).map(function (name) {
    return {
      name: name,
      count: groups[name].members.length,
      locked: groups[name].password !== null,
    };
  });
  wss.clients.forEach(function (c) {
    if (c.userName) sendTo(c, { type: 'groups', groups: list });
  });
}

function sendMemberList(groupName) {
  const names = groups[groupName].members.map(function (c) { return c.userName; });
  sendToGroup(groupName, { type: 'members', group: groupName, members: names });
}

// "Ali joined the group" style messages
function sendSystemMessage(groupName, text) {
  const msg = { type: 'system', group: groupName, text: text, time: new Date().toISOString() };
  groups[groupName].history.push(msg);
  sendToGroup(groupName, msg);
}

function joinGroup(client, groupName) {
  const group = groups[groupName];

  // already inside? just show it again
  if (group.members.includes(client)) {
    return sendTo(client, { type: 'joined', group: groupName, history: group.history });
  }

  group.members.push(client);
  client.myGroups.push(groupName);

  // the new member receives the old messages of the group
  sendTo(client, { type: 'joined', group: groupName, history: group.history });
  sendSystemMessage(groupName, client.userName + ' joined the group');
  sendMemberList(groupName);
}

function leaveGroup(client, groupName) {
  const group = groups[groupName];
  if (!group || !group.members.includes(client)) return;

  group.members = group.members.filter(function (c) { return c !== client; });
  client.myGroups = client.myGroups.filter(function (g) { return g !== groupName; });

  // delete empty groups (but never "General")
  if (group.members.length === 0 && groupName !== 'General') {
    delete groups[groupName];
    return;
  }
  sendSystemMessage(groupName, client.userName + ' left the group');
  sendMemberList(groupName);
}

// ---------- What happens when a client connects ----------

wss.on('connection', function (client) {

  client.on('message', function (raw) {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch (e) {
      return; // ignore broken data
    }

    // --- login: the first thing every client must do ---
    if (msg.type === 'login' && !client.userName) {
      const name = cleanText(msg.name, 20);
      if (!name) {
        return sendTo(client, { type: 'error', scope: 'login', text: 'Name required' });
      }
      if (nameIsTaken(name)) {
        return sendTo(client, { type: 'error', scope: 'login', text: 'That name is already in use' });
      }
      client.userName = name;
      client.myGroups = [];
      sendTo(client, { type: 'welcome', name: name });
      joinGroup(client, 'General'); // everyone starts in General
      sendGroupListToEveryone();
      return;
    }

    if (!client.userName) return; // not logged in yet

    // --- create a new group ---
    if (msg.type === 'create') {
      const name = cleanText(msg.group, 24);
      if (!name) return;
      if (findGroupName(name)) {
        return sendTo(client, { type: 'error', scope: 'group', text: 'A group with that name already exists' });
      }
      groups[name] = { password: msg.password ? String(msg.password) : null, members: [], history: [] };
      joinGroup(client, name);
      sendGroupListToEveryone();
    }

    // --- join an existing group (password needed if it has one) ---
    else if (msg.type === 'join') {
      const name = findGroupName(cleanText(msg.group, 24) || '');
      if (!name) {
        return sendTo(client, { type: 'error', scope: 'group', text: 'Group not found' });
      }
      const group = groups[name];
      const alreadyIn = group.members.includes(client);
      if (group.password && !alreadyIn && group.password !== String(msg.password || '')) {
        return sendTo(client, { type: 'error', scope: 'group', text: 'Wrong password' });
      }
      joinGroup(client, name);
      sendGroupListToEveryone();
    }

    // --- leave a group ---
    else if (msg.type === 'leave') {
      const name = findGroupName(cleanText(msg.group, 24) || '');
      if (name) {
        leaveGroup(client, name);
        sendTo(client, { type: 'left', group: name });
        sendGroupListToEveryone();
      }
    }

    // --- chat message: goes ONLY to the members of that group ---
    else if (msg.type === 'message') {
      const name = findGroupName(cleanText(msg.group, 24) || '');
      const text = cleanText(msg.text, 1000);
      if (!name || !text) return;
      if (!groups[name].members.includes(client)) return; // must be a member

      const chatMessage = { type: 'message', group: name, name: client.userName, text: text, time: new Date().toISOString() };
      groups[name].history.push(chatMessage);
      if (groups[name].history.length > MAX_HISTORY) groups[name].history.shift();
      sendToGroup(name, chatMessage);
    }
  });

  // when the user closes the page: remove them from all their groups
  client.on('close', function () {
    if (!client.userName) return;
    client.myGroups.slice().forEach(function (g) { leaveGroup(client, g); });
    sendGroupListToEveryone();
  });
});

httpServer.listen(PORT, function () {
  console.log('Chat server running on http://localhost:' + PORT);
});

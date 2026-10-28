// One server: serves the web client over HTTP and handles chat over WebSocket.
//   Step 1: two clients chat through the server
//   Step 2: any number of clients; every message is broadcast
//   Step 3: clients live in groups; broadcast only reaches the sender's group
const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 3000;
const PUBLIC = path.join(__dirname, 'public');
const MIME = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript' };
const MAX_TEXT = 1000;

const httpServer = http.createServer((req, res) => {
  const file = req.url === '/' ? '/index.html' : req.url.split('?')[0];
  const full = path.join(PUBLIC, path.normalize(file));
  if (!full.startsWith(PUBLIC)) { res.writeHead(403); return res.end(); }
  fs.readFile(full, (err, data) => {
    if (err) { res.writeHead(404); return res.end('Not found'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(full)] || 'application/octet-stream' });
    res.end(data);
  });
});

const wss = new WebSocketServer({ server: httpServer });

// group name -> Set of client sockets; socket.user = { name, group }
const groups = new Map([['General', new Set()]]);

const send = (ws, obj) => ws.readyState === 1 && ws.send(JSON.stringify(obj));
const now = () => new Date().toISOString();

function broadcastGroup(group, obj) {
  for (const ws of groups.get(group) || []) send(ws, obj);
}

function groupsSnapshot() {
  return [...groups].map(([name, set]) => ({ name, count: set.size }));
}

function pushGroupList() {
  const msg = { type: 'groups', groups: groupsSnapshot() };
  for (const ws of wss.clients) if (ws.user) send(ws, msg);
}

function pushMembers(group) {
  const members = [...(groups.get(group) || [])].map((w) => w.user.name);
  broadcastGroup(group, { type: 'members', group, members });
}

function leave(ws) {
  const { group, name } = ws.user;
  const set = groups.get(group);
  if (!set) return;
  set.delete(ws);
  if (set.size === 0 && group !== 'General') groups.delete(group);
  else {
    broadcastGroup(group, { type: 'system', text: `${name} left the group`, time: now() });
    pushMembers(group);
  }
}

function enter(ws, group) {
  if (!groups.has(group)) groups.set(group, new Set());
  groups.get(group).add(ws);
  ws.user.group = group;
  send(ws, { type: 'joined', group });
  broadcastGroup(group, { type: 'system', text: `${ws.user.name} joined the group`, time: now() });
  pushMembers(group);
}

const clean = (s, max) => String(s || '').replace(/\s+/g, ' ').trim().slice(0, max);

wss.on('connection', (ws) => {
  ws.on('message', (raw) => {
    let m;
    try { m = JSON.parse(raw); } catch { return; }

    if (m.type === 'join' && !ws.user) {
      const name = clean(m.name, 20);
      const group = clean(m.group, 24) || 'General';
      if (!name) return send(ws, { type: 'error', text: 'Name required' });
      const taken = [...wss.clients].some((c) => c.user && c.user.name.toLowerCase() === name.toLowerCase());
      if (taken) return send(ws, { type: 'error', text: 'That name is already in use' });
      ws.user = { name, group: null };
      enter(ws, group);
      pushGroupList();
    } else if (m.type === 'switch' && ws.user) {
      const group = clean(m.group, 24);
      if (!group || group === ws.user.group) return;
      leave(ws);
      enter(ws, group);
      pushGroupList();
    } else if (m.type === 'message' && ws.user) {
      const text = clean(m.text, MAX_TEXT);
      if (!text) return;
      broadcastGroup(ws.user.group, { type: 'message', name: ws.user.name, text, time: now() });
    }
  });

  ws.on('close', () => {
    if (!ws.user || !ws.user.group) return;
    leave(ws);
    pushGroupList();
  });
});

httpServer.listen(PORT, () => console.log(`Chat server running on http://localhost:${PORT}`));

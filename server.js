// One server: serves the web client over HTTP and handles chat over WebSocket.
//   Step 1/2: any number of clients chat through the server
//   Step 3: groups - a message is broadcast only to the members of its group
// Groups are listed for everyone; a protected group needs its password to join.
const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 3000;
const PUBLIC = path.join(__dirname, 'public');
const MIME = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript' };
const MAX_TEXT = 1000;
const HISTORY = 100;

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

// name -> { password: string|null, members: Set<ws>, history: [] }
const groups = new Map([['General', { password: null, members: new Set(), history: [] }]]);

const send = (ws, obj) => ws.readyState === 1 && ws.send(JSON.stringify(obj));
const now = () => new Date().toISOString();
const clean = (s, max) => String(s || '').replace(/\s+/g, ' ').trim().slice(0, max);
const findGroup = (name) => [...groups.keys()].find((k) => k.toLowerCase() === name.toLowerCase());

function broadcastGroup(name, obj) {
  for (const ws of groups.get(name).members) send(ws, obj);
}

function pushGroupList() {
  const list = [...groups].map(([name, g]) => ({ name, count: g.members.size, locked: !!g.password }));
  for (const ws of wss.clients) if (ws.user) send(ws, { type: 'groups', groups: list });
}

function pushMembers(name) {
  const g = groups.get(name);
  broadcastGroup(name, { type: 'members', group: name, members: [...g.members].map((w) => w.user.name) });
}

function systemMsg(name, text) {
  const m = { type: 'system', group: name, text, time: now() };
  groups.get(name).history.push(m);
  broadcastGroup(name, m);
}

function enter(ws, name) {
  const g = groups.get(name);
  if (g.members.has(ws)) return send(ws, { type: 'joined', group: name, history: g.history });
  g.members.add(ws);
  ws.user.groups.add(name);
  send(ws, { type: 'joined', group: name, history: g.history });
  systemMsg(name, `${ws.user.name} joined the group`);
  pushMembers(name);
}

function leave(ws, name) {
  const g = groups.get(name);
  if (!g || !g.members.delete(ws)) return;
  ws.user.groups.delete(name);
  if (g.members.size === 0 && name !== 'General') return void groups.delete(name);
  systemMsg(name, `${ws.user.name} left the group`);
  pushMembers(name);
}

wss.on('connection', (ws) => {
  ws.on('message', (raw) => {
    let m;
    try { m = JSON.parse(raw); } catch { return; }

    if (m.type === 'login' && !ws.user) {
      const name = clean(m.name, 20);
      if (!name) return send(ws, { type: 'error', scope: 'login', text: 'Name required' });
      const taken = [...wss.clients].some((c) => c.user && c.user.name.toLowerCase() === name.toLowerCase());
      if (taken) return send(ws, { type: 'error', scope: 'login', text: 'That name is already in use' });
      ws.user = { name, groups: new Set() };
      send(ws, { type: 'welcome', name });
      enter(ws, 'General');
      pushGroupList();
      return;
    }
    if (!ws.user) return;

    if (m.type === 'create') {
      const name = clean(m.group, 24);
      if (!name) return;
      if (findGroup(name)) return send(ws, { type: 'error', scope: 'group', text: 'A group with that name already exists' });
      groups.set(name, { password: String(m.password || '') || null, members: new Set(), history: [] });
      enter(ws, name);
      pushGroupList();
    } else if (m.type === 'join') {
      const name = findGroup(clean(m.group, 24));
      if (!name) return send(ws, { type: 'error', scope: 'group', text: 'Group not found' });
      const g = groups.get(name);
      if (g.password && g.password !== String(m.password || '') && !g.members.has(ws))
        return send(ws, { type: 'error', scope: 'group', text: 'Wrong password' });
      enter(ws, name);
      pushGroupList();
    } else if (m.type === 'leave') {
      const name = findGroup(clean(m.group, 24));
      if (name) { leave(ws, name); send(ws, { type: 'left', group: name }); pushGroupList(); }
    } else if (m.type === 'message') {
      const name = findGroup(clean(m.group, 24));
      const text = clean(m.text, MAX_TEXT);
      if (!name || !text || !groups.get(name).members.has(ws)) return;
      const msg = { type: 'message', group: name, name: ws.user.name, text, time: now() };
      const g = groups.get(name);
      g.history.push(msg);
      if (g.history.length > HISTORY) g.history.shift();
      broadcastGroup(name, msg);
    }
  });

  ws.on('close', () => {
    if (!ws.user) return;
    for (const name of [...ws.user.groups]) leave(ws, name);
    pushGroupList();
  });
});

httpServer.listen(PORT, () => console.log(`Chat server running on http://localhost:${PORT}`));

const $ = (id) => document.getElementById(id);
let ws, me = '', current = '';

const hue = (s) => [...s].reduce((a, c) => a + c.charCodeAt(0) * 7, 0) % 360;
function avatar(el, name) {
  el.textContent = name[0].toUpperCase();
  el.style.background = `hsl(${hue(name)} 65% 50%)`;
}
const fmt = (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

function connect(name, group) {
  ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`);
  ws.onopen = () => ws.send(JSON.stringify({ type: 'join', name, group }));
  ws.onclose = () => { $('status').textContent = 'disconnected'; $('status').style.color = '#ff6b81'; };
  ws.onmessage = (e) => handle(JSON.parse(e.data));
  me = name;
}

function handle(m) {
  if (m.type === 'error') $('loginError').textContent = m.text;
  else if (m.type === 'joined') {
    current = m.group;
    $('login').classList.add('hidden'); $('app').classList.remove('hidden');
    $('meName').textContent = me; avatar($('meAvatar'), me);
    $('groupTitle').textContent = '# ' + m.group; $('messages').innerHTML = '';
    $('textInput').focus();
  } else if (m.type === 'groups') renderGroups(m.groups);
  else if (m.type === 'members') renderMembers(m.members);
  else if (m.type === 'message') addMessage(m);
  else if (m.type === 'system') {
    const d = document.createElement('div'); d.className = 'sys'; d.textContent = m.text;
    add(d);
  }
}

function add(node) {
  const box = $('messages');
  const stick = box.scrollTop + box.clientHeight >= box.scrollHeight - 40;
  box.appendChild(node);
  if (stick) box.scrollTop = box.scrollHeight;
}

function addMessage(m) {
  const d = document.createElement('div'); d.className = 'msg' + (m.name === me ? ' me' : '');
  const who = document.createElement('div'); who.className = 'who'; who.textContent = m.name;
  who.style.color = `hsl(${hue(m.name)} 80% 70%)`;
  const t = document.createElement('time'); t.textContent = fmt(m.time); who.appendChild(t);
  const body = document.createElement('span'); body.textContent = m.text;
  if (m.name === me) { const t2 = document.createElement('time'); t2.textContent = fmt(m.time); d.append(body, t2); }
  else d.append(who, body);
  add(d);
}

function renderGroups(groups) {
  const ul = $('groupList'); ul.innerHTML = '';
  for (const g of groups) {
    const li = document.createElement('li'); if (g.name === current) li.className = 'active';
    const a = document.createElement('span'); a.textContent = '# ' + g.name;
    const n = document.createElement('span'); n.className = 'n'; n.textContent = g.count;
    li.append(a, n); li.onclick = () => ws.send(JSON.stringify({ type: 'switch', group: g.name }));
    ul.appendChild(li);
  }
}

function renderMembers(list) {
  $('memberCount').textContent = list.length + ' online';
  const ul = $('memberList'); ul.innerHTML = '';
  for (const n of list) {
    const li = document.createElement('li'); const av = document.createElement('span'); av.className = 'avatar';
    avatar(av, n); const s = document.createElement('span'); s.textContent = n;
    li.append(av, s); ul.appendChild(li);
  }
}

$('loginForm').onsubmit = (e) => { e.preventDefault(); $('loginError').textContent = ''; connect($('nameInput').value.trim(), $('groupInput').value.trim()); };
$('sendForm').onsubmit = (e) => {
  e.preventDefault(); const text = $('textInput').value.trim(); if (!text) return;
  ws.send(JSON.stringify({ type: 'message', text })); $('textInput').value = '';
};
$('newGroupForm').onsubmit = (e) => {
  e.preventDefault(); const g = $('newGroupInput').value.trim(); if (!g) return;
  ws.send(JSON.stringify({ type: 'switch', group: g })); $('newGroupInput').value = '';
};

const $ = (id) => document.getElementById(id);
let ws, me = '', active = null, pendingJoin = null;
let allGroups = [];
const joined = new Set(), logs = {}, membersBy = {}, unread = {};

const hue = (s) => [...s].reduce((a, c) => a + c.charCodeAt(0) * 7, 0) % 360;
function avatar(el, name) {
  el.textContent = name[0].toUpperCase();
  el.style.background = `hsl(${hue(name)} 65% 50%)`;
}
const fmt = (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
const sendJson = (o) => ws.send(JSON.stringify(o));
const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; };

function connect(name) {
  ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`);
  ws.onopen = () => sendJson({ type: 'login', name });
  ws.onclose = () => { $('status').textContent = 'disconnected'; $('status').style.color = '#ff6b81'; };
  ws.onmessage = (e) => handle(JSON.parse(e.data));
  me = name;
}

function handle(m) {
  if (m.type === 'error') {
    if (m.scope === 'login') $('loginError').textContent = m.text;
    else if ($('pwDialog').open) $('pwError').textContent = m.text;
    else $('groupError').textContent = m.text;
  } else if (m.type === 'welcome') {
    $('login').classList.add('hidden'); $('app').classList.remove('hidden');
    $('meName').textContent = me; avatar($('meAvatar'), me);
  } else if (m.type === 'joined') {
    joined.add(m.group); logs[m.group] = m.history.slice(); unread[m.group] = 0;
    if ($('pwDialog').open) $('pwDialog').close();
    $('groupError').textContent = '';
    setActive(m.group);
  } else if (m.type === 'left') {
    joined.delete(m.group); delete logs[m.group];
    if (active === m.group) setActive([...joined][0] || null); else renderGroups();
  } else if (m.type === 'groups') { allGroups = m.groups; renderGroups(); }
  else if (m.type === 'members') {
    membersBy[m.group] = m.members;
    if (m.group === active) renderMembers();
  } else if (m.type === 'message' || m.type === 'system') {
    if (!logs[m.group]) return;
    logs[m.group].push(m);
    if (m.group === active) appendItem(m);
    else if (m.type === 'message') { unread[m.group] = (unread[m.group] || 0) + 1; renderGroups(); }
  }
}

function setActive(group) {
  active = group; if (group) unread[group] = 0;
  $('groupTitle').textContent = group ? '# ' + group : 'Join a group';
  $('leaveBtn').classList.toggle('hidden', !group);
  $('textInput').disabled = !group;
  $('messages').innerHTML = '';
  if (group) { logs[group].forEach(appendItem); $('textInput').focus(); }
  else $('messages').appendChild(el('div', 'sys', 'Pick a group from the list to start chatting'));
  renderGroups(); renderMembers();
}

function appendItem(m) {
  const box = $('messages');
  const stick = box.scrollTop + box.clientHeight >= box.scrollHeight - 40;
  const mine = m.name === me;
  let node;
  if (m.type === 'system') node = el('div', 'sys', m.text);
  else {
    node = el('div', 'msg' + (mine ? ' me' : ''));
    const t = el('time', null, fmt(m.time));
    if (mine) node.append(el('span', null, m.text), t);
    else {
      const who = el('div', 'who', m.name); who.style.color = `hsl(${hue(m.name)} 80% 70%)`; who.appendChild(t);
      node.append(who, el('span', null, m.text));
    }
  }
  box.appendChild(node);
  if (stick || mine) box.scrollTop = box.scrollHeight;
}

function renderGroups() {
  const ul = $('groupList'); ul.innerHTML = '';
  for (const g of allGroups) {
    const li = el('li', g.name === active ? 'active' : joined.has(g.name) ? 'in' : '');
    li.append(el('span', null, (g.locked ? '🔒 ' : '# ') + g.name));
    const right = el('span', 'right');
    if (unread[g.name]) right.appendChild(el('span', 'badge', unread[g.name]));
    right.appendChild(el('span', 'n', g.count));
    li.appendChild(right);
    li.onclick = () => onGroupClick(g);
    ul.appendChild(li);
  }
}

function onGroupClick(g) {
  if (joined.has(g.name)) return setActive(g.name);
  if (!g.locked) return sendJson({ type: 'join', group: g.name });
  pendingJoin = g.name;
  $('pwTitle').textContent = '🔒 ' + g.name; $('pwInput').value = ''; $('pwError').textContent = '';
  $('pwDialog').showModal(); $('pwInput').focus();
}

function renderMembers() {
  const list = (active && membersBy[active]) || [];
  $('memberCount').textContent = active ? list.length + ' online' : '';
  const ul = $('memberList'); ul.innerHTML = '';
  for (const n of list) {
    const li = el('li'); const av = el('span', 'avatar'); avatar(av, n);
    li.append(av, el('span', null, n)); ul.appendChild(li);
  }
}

$('loginForm').onsubmit = (e) => { e.preventDefault(); $('loginError').textContent = ''; connect($('nameInput').value.trim()); };
$('sendForm').onsubmit = (e) => {
  e.preventDefault(); const text = $('textInput').value.trim(); if (!text || !active) return;
  sendJson({ type: 'message', group: active, text }); $('textInput').value = '';
};
$('newGroupForm').onsubmit = (e) => {
  e.preventDefault(); $('groupError').textContent = '';
  sendJson({ type: 'create', group: $('newGroupInput').value.trim(), password: $('newPassInput').value });
  $('newGroupInput').value = ''; $('newPassInput').value = '';
};
$('pwForm').onsubmit = (e) => { e.preventDefault(); sendJson({ type: 'join', group: pendingJoin, password: $('pwInput').value }); };
$('pwCancel').onclick = () => $('pwDialog').close();
$('leaveBtn').onclick = () => active && sendJson({ type: 'leave', group: active });

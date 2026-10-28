// Browser side of the chat. It talks to the server over a WebSocket and
// draws everything on the page.

// shortcut: $('name') = document.getElementById('name')
function $(id) { return document.getElementById(id); }

let socket;              // the connection to the server
let myName = '';
let activeGroup = null;  // the group whose chat is shown right now
let groupToJoin = null;  // group waiting for a password
let allGroups = [];      // every group on the server (sent by the server)

const joinedGroups = [];  // names of the groups I am inside
const messagesByGroup = {}; // group name -> list of messages I can see
const membersByGroup = {};  // group name -> list of names online in it
const unreadCount = {};     // group name -> number of new messages

// ---------- small helpers ----------

// each name gets its own color
function colorFor(name) {
  let sum = 0;
  for (const letter of name) sum += letter.charCodeAt(0) * 7;
  return sum % 360;
}

function showAvatar(element, name) {
  element.textContent = name[0].toUpperCase();
  element.style.background = 'hsl(' + colorFor(name) + ' 65% 50%)';
}

function formatTime(isoString) {
  return new Date(isoString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function sendToServer(data) {
  socket.send(JSON.stringify(data));
}

// create an element with an optional css class and text
// (textContent is used so nobody can inject HTML into the chat)
function makeElement(tag, className, text) {
  const e = document.createElement(tag);
  if (className) e.className = className;
  if (text !== undefined) e.textContent = text;
  return e;
}

// ---------- connecting ----------

function connect(name) {
  myName = name;
  const protocol = location.protocol === 'https:' ? 'wss://' : 'ws://';
  socket = new WebSocket(protocol + location.host);

  socket.onopen = function () {
    sendToServer({ type: 'login', name: name });
  };
  socket.onmessage = function (event) {
    handleServerMessage(JSON.parse(event.data));
  };
  socket.onclose = function () {
    $('status').textContent = 'disconnected';
    $('status').style.color = '#ff6b81';
  };
}

// ---------- everything the server can tell us ----------

function handleServerMessage(m) {

  if (m.type === 'error') {
    // show the error in the place the user is looking at
    if (m.scope === 'login') $('loginError').textContent = m.text;
    else if ($('pwDialog').open) $('pwError').textContent = m.text;
    else $('groupError').textContent = m.text;
  }

  else if (m.type === 'welcome') {
    // login worked: hide the login box, show the chat
    $('login').classList.add('hidden');
    $('app').classList.remove('hidden');
    $('meName').textContent = myName;
    showAvatar($('meAvatar'), myName);
  }

  else if (m.type === 'joined') {
    // I am now inside a group; the server sent its old messages
    if (!joinedGroups.includes(m.group)) joinedGroups.push(m.group);
    messagesByGroup[m.group] = m.history.slice();
    unreadCount[m.group] = 0;
    if ($('pwDialog').open) $('pwDialog').close();
    $('groupError').textContent = '';
    openGroup(m.group);
  }

  else if (m.type === 'left') {
    joinedGroups.splice(joinedGroups.indexOf(m.group), 1);
    delete messagesByGroup[m.group];
    if (activeGroup === m.group) openGroup(joinedGroups[0] || null);
    else drawGroupList();
  }

  else if (m.type === 'groups') {
    allGroups = m.groups;
    drawGroupList();
  }

  else if (m.type === 'members') {
    membersByGroup[m.group] = m.members;
    if (m.group === activeGroup) drawMembers();
  }

  else if (m.type === 'message' || m.type === 'system') {
    if (!messagesByGroup[m.group]) return; // not my group
    messagesByGroup[m.group].push(m);

    if (m.group === activeGroup) {
      drawMessage(m);
    } else if (m.type === 'message') {
      unreadCount[m.group] = (unreadCount[m.group] || 0) + 1; // red badge
      drawGroupList();
    }
  }
}

// ---------- drawing on the page ----------

// show the chat of one group (or an empty screen if group is null)
function openGroup(group) {
  activeGroup = group;
  if (group) unreadCount[group] = 0;

  $('groupTitle').textContent = group ? '# ' + group : 'Join a group';
  $('leaveBtn').classList.toggle('hidden', !group);
  $('textInput').disabled = !group;
  $('messages').innerHTML = '';

  if (group) {
    messagesByGroup[group].forEach(drawMessage);
    $('textInput').focus();
  } else {
    $('messages').appendChild(makeElement('div', 'sys', 'Pick a group from the list to start chatting'));
  }
  drawGroupList();
  drawMembers();
}

function drawMessage(m) {
  const box = $('messages');
  const wasAtBottom = box.scrollTop + box.clientHeight >= box.scrollHeight - 40;
  const isMine = m.name === myName;
  let node;

  if (m.type === 'system') {
    node = makeElement('div', 'sys', m.text);
  } else if (isMine) {
    node = makeElement('div', 'msg me');
    node.append(makeElement('span', '', m.text), makeElement('time', '', formatTime(m.time)));
  } else {
    node = makeElement('div', 'msg');
    const who = makeElement('div', 'who', m.name);
    who.style.color = 'hsl(' + colorFor(m.name) + ' 80% 70%)';
    who.appendChild(makeElement('time', '', formatTime(m.time)));
    node.append(who, makeElement('span', '', m.text));
  }

  box.appendChild(node);
  if (wasAtBottom || isMine) box.scrollTop = box.scrollHeight; // auto-scroll
}

function drawGroupList() {
  const list = $('groupList');
  list.innerHTML = '';

  allGroups.forEach(function (g) {
    let cssClass = '';
    if (g.name === activeGroup) cssClass = 'active';
    else if (joinedGroups.includes(g.name)) cssClass = 'in';

    const item = makeElement('li', cssClass);
    item.appendChild(makeElement('span', '', (g.locked ? '🔒 ' : '# ') + g.name));

    const right = makeElement('span', 'right');
    if (unreadCount[g.name]) right.appendChild(makeElement('span', 'badge', unreadCount[g.name]));
    right.appendChild(makeElement('span', 'n', g.count));
    item.appendChild(right);

    item.onclick = function () { clickOnGroup(g); };
    list.appendChild(item);
  });
}

function clickOnGroup(g) {
  if (joinedGroups.includes(g.name)) {
    openGroup(g.name);                               // already inside: just open it
  } else if (!g.locked) {
    sendToServer({ type: 'join', group: g.name });   // public: join directly
  } else {
    // protected: ask for the password first
    groupToJoin = g.name;
    $('pwTitle').textContent = '🔒 ' + g.name;
    $('pwInput').value = '';
    $('pwError').textContent = '';
    $('pwDialog').showModal();
    $('pwInput').focus();
  }
}

function drawMembers() {
  const names = (activeGroup && membersByGroup[activeGroup]) || [];
  $('memberCount').textContent = activeGroup ? names.length + ' online' : '';
  const list = $('memberList');
  list.innerHTML = '';

  names.forEach(function (name) {
    const item = makeElement('li');
    const avatar = makeElement('span', 'avatar');
    showAvatar(avatar, name);
    item.append(avatar, makeElement('span', '', name));
    list.appendChild(item);
  });
}

// ---------- buttons and forms ----------

$('loginForm').onsubmit = function (e) {
  e.preventDefault(); // stop the page from reloading
  $('loginError').textContent = '';
  connect($('nameInput').value.trim());
};

$('sendForm').onsubmit = function (e) {
  e.preventDefault();
  const text = $('textInput').value.trim();
  if (!text || !activeGroup) return;
  sendToServer({ type: 'message', group: activeGroup, text: text });
  $('textInput').value = '';
};

$('newGroupForm').onsubmit = function (e) {
  e.preventDefault();
  $('groupError').textContent = '';
  sendToServer({ type: 'create', group: $('newGroupInput').value.trim(), password: $('newPassInput').value });
  $('newGroupInput').value = '';
  $('newPassInput').value = '';
};

$('pwForm').onsubmit = function (e) {
  e.preventDefault();
  sendToServer({ type: 'join', group: groupToJoin, password: $('pwInput').value });
};

$('pwCancel').onclick = function () { $('pwDialog').close(); };
$('leaveBtn').onclick = function () {
  if (activeGroup) sendToServer({ type: 'leave', group: activeGroup });
};

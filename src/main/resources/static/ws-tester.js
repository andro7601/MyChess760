const START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
const WHITE_TIME_MS = 10 * 60 * 1000;
const PIECES = {
    wk: "♔", wq: "♕", wr: "♖", wb: "♗", wn: "♘", wp: "♙",
    bk: "♚", bq: "♛", br: "♜", bb: "♝", bn: "♞", bp: "♟"
};

const $ = (id) => document.getElementById(id);
const sameId = (a, b) => a !== null && a !== undefined && b !== null && b !== undefined && String(a) === String(b);
const opposite = (color) => color === "w" ? "b" : "w";

function squareToCoords(square) {
    return [square.charCodeAt(0) - 97, Number(square[1]) - 1];
}

function coordsToSquare(file, rank) {
    if (file < 0 || file > 7 || rank < 0 || rank > 7) return null;
    return String.fromCharCode(97 + file) + String(rank + 1);
}

class LocalChess {
    constructor(fen = START_FEN) {
        this.load(fen);
    }

    load(fen = START_FEN) {
        const parts = fen.trim().split(/\s+/);
        const rows = parts[0].split("/");
        this.board = new Map();
        rows.forEach((row, rowIndex) => {
            let file = 0;
            const rank = 7 - rowIndex;
            for (const char of row) {
                if (/\d/.test(char)) {
                    file += Number(char);
                } else {
                    const color = char === char.toUpperCase() ? "w" : "b";
                    this.board.set(coordsToSquare(file, rank), { color, type: char.toLowerCase() });
                    file += 1;
                }
            }
        });
        this.side = parts[1] || "w";
        this.castling = parts[2] && parts[2] !== "-" ? parts[2] : "";
        this.ep = parts[3] && parts[3] !== "-" ? parts[3] : null;
        this.halfmove = Number(parts[4] || 0);
        this.fullmove = Number(parts[5] || 1);
    }

    clone() {
        return new LocalChess(this.fen());
    }

    get(square) {
        const piece = this.board.get(square);
        return piece ? { ...piece } : null;
    }

    turn() {
        return this.side;
    }

    fen() {
        const rows = [];
        for (let rank = 7; rank >= 0; rank--) {
            let row = "";
            let empty = 0;
            for (let file = 0; file < 8; file++) {
                const piece = this.board.get(coordsToSquare(file, rank));
                if (!piece) {
                    empty += 1;
                    continue;
                }
                if (empty) {
                    row += String(empty);
                    empty = 0;
                }
                const letter = piece.color === "w" ? piece.type.toUpperCase() : piece.type;
                row += letter;
            }
            rows.push(row + (empty ? String(empty) : ""));
        }
        return [
            rows.join("/"),
            this.side,
            this.castling || "-",
            this.ep || "-",
            String(this.halfmove),
            String(this.fullmove)
        ].join(" ");
    }

    moves(options = {}) {
        const square = options.square || null;
        const verbose = Boolean(options.verbose);
        const candidates = [];
        const scan = square ? [[square, this.board.get(square)]] : Array.from(this.board.entries());
        for (const [from, piece] of scan) {
            if (!piece || piece.color !== this.side) continue;
            candidates.push(...this.pseudoMovesFor(from, piece));
        }
        const legal = candidates.filter((move) => {
            const copy = this.clone();
            copy.applyUnchecked(move);
            return !copy.inCheck(move.color);
        });
        return verbose ? legal : legal.map((move) => move.from + move.to + (move.promotion || ""));
    }

    move(input) {
        const from = input.from;
        const to = input.to;
        const promotion = input.promotion || "q";
        const found = this.moves({ square: from, verbose: true })
            .find((candidate) => candidate.to === to && (!candidate.promotion || candidate.promotion === promotion));
        if (!found) return null;
        found.promotion = found.promotion || input.promotion || undefined;
        this.applyUnchecked(found);
        return found;
    }

    pseudoMovesFor(from, piece) {
        if (piece.type === "p") return this.pawnMoves(from, piece);
        if (piece.type === "n") return this.stepMoves(from, piece, [[1, 2], [2, 1], [2, -1], [1, -2], [-1, -2], [-2, -1], [-2, 1], [-1, 2]]);
        if (piece.type === "b") return this.slideMoves(from, piece, [[1, 1], [1, -1], [-1, -1], [-1, 1]]);
        if (piece.type === "r") return this.slideMoves(from, piece, [[1, 0], [-1, 0], [0, 1], [0, -1]]);
        if (piece.type === "q") return this.slideMoves(from, piece, [[1, 1], [1, -1], [-1, -1], [-1, 1], [1, 0], [-1, 0], [0, 1], [0, -1]]);
        if (piece.type === "k") return this.kingMoves(from, piece);
        return [];
    }

    pawnMoves(from, piece) {
        const [file, rank] = squareToCoords(from);
        const dir = piece.color === "w" ? 1 : -1;
        const startRank = piece.color === "w" ? 1 : 6;
        const promoRank = piece.color === "w" ? 7 : 0;
        const moves = [];
        const addPawnMove = (to, captured, flags = "") => {
            if (!to) return;
            const [, targetRank] = squareToCoords(to);
            const move = { from, to, color: piece.color, piece: "p", captured, flags };
            if (targetRank === promoRank) move.promotion = "q";
            moves.push(move);
        };

        const one = coordsToSquare(file, rank + dir);
        if (one && !this.board.has(one)) {
            addPawnMove(one, null);
            const two = coordsToSquare(file, rank + 2 * dir);
            if (rank === startRank && two && !this.board.has(two)) {
                addPawnMove(two, null, "b");
            }
        }

        for (const df of [-1, 1]) {
            const target = coordsToSquare(file + df, rank + dir);
            if (!target) continue;
            const targetPiece = this.board.get(target);
            if (targetPiece && targetPiece.color !== piece.color) {
                addPawnMove(target, targetPiece.type, "c");
            } else if (this.ep === target) {
                addPawnMove(target, "p", "e");
            }
        }
        return moves;
    }

    stepMoves(from, piece, offsets) {
        const [file, rank] = squareToCoords(from);
        const moves = [];
        for (const [df, dr] of offsets) {
            const to = coordsToSquare(file + df, rank + dr);
            if (!to) continue;
            const target = this.board.get(to);
            if (!target || target.color !== piece.color) {
                moves.push({ from, to, color: piece.color, piece: piece.type, captured: target?.type || null, flags: target ? "c" : "" });
            }
        }
        return moves;
    }

    slideMoves(from, piece, directions) {
        const [file, rank] = squareToCoords(from);
        const moves = [];
        for (const [df, dr] of directions) {
            let nextFile = file + df;
            let nextRank = rank + dr;
            while (true) {
                const to = coordsToSquare(nextFile, nextRank);
                if (!to) break;
                const target = this.board.get(to);
                if (!target) {
                    moves.push({ from, to, color: piece.color, piece: piece.type, captured: null, flags: "" });
                } else {
                    if (target.color !== piece.color) {
                        moves.push({ from, to, color: piece.color, piece: piece.type, captured: target.type, flags: "c" });
                    }
                    break;
                }
                nextFile += df;
                nextRank += dr;
            }
        }
        return moves;
    }

    kingMoves(from, piece) {
        const moves = this.stepMoves(from, piece, [[1, 1], [1, 0], [1, -1], [0, 1], [0, -1], [-1, 1], [-1, 0], [-1, -1]]);
        const homeRank = piece.color === "w" ? 0 : 7;
        const kingHome = coordsToSquare(4, homeRank);
        if (from !== kingHome || this.inCheck(piece.color)) return moves;
        const enemy = opposite(piece.color);
        const kingSideFlag = piece.color === "w" ? "K" : "k";
        const queenSideFlag = piece.color === "w" ? "Q" : "q";
        if (this.castling.includes(kingSideFlag)
            && this.board.get(coordsToSquare(7, homeRank))?.type === "r"
            && !this.board.has(coordsToSquare(5, homeRank))
            && !this.board.has(coordsToSquare(6, homeRank))
            && !this.isSquareAttacked(coordsToSquare(5, homeRank), enemy)
            && !this.isSquareAttacked(coordsToSquare(6, homeRank), enemy)) {
            moves.push({ from, to: coordsToSquare(6, homeRank), color: piece.color, piece: "k", captured: null, flags: "k" });
        }
        if (this.castling.includes(queenSideFlag)
            && this.board.get(coordsToSquare(0, homeRank))?.type === "r"
            && !this.board.has(coordsToSquare(1, homeRank))
            && !this.board.has(coordsToSquare(2, homeRank))
            && !this.board.has(coordsToSquare(3, homeRank))
            && !this.isSquareAttacked(coordsToSquare(2, homeRank), enemy)
            && !this.isSquareAttacked(coordsToSquare(3, homeRank), enemy)) {
            moves.push({ from, to: coordsToSquare(2, homeRank), color: piece.color, piece: "k", captured: null, flags: "q" });
        }
        return moves;
    }

    applyUnchecked(move) {
        const piece = this.board.get(move.from);
        if (!piece) return;
        const [fromFile, fromRank] = squareToCoords(move.from);
        const [toFile, toRank] = squareToCoords(move.to);

        this.board.delete(move.from);
        if (move.flags.includes("e")) {
            this.board.delete(coordsToSquare(toFile, fromRank));
        }
        if (piece.type === "k" && Math.abs(toFile - fromFile) === 2) {
            if (toFile === 6) {
                const rookFrom = coordsToSquare(7, fromRank);
                const rookTo = coordsToSquare(5, fromRank);
                const rook = this.board.get(rookFrom);
                this.board.delete(rookFrom);
                if (rook) this.board.set(rookTo, rook);
            } else if (toFile === 2) {
                const rookFrom = coordsToSquare(0, fromRank);
                const rookTo = coordsToSquare(3, fromRank);
                const rook = this.board.get(rookFrom);
                this.board.delete(rookFrom);
                if (rook) this.board.set(rookTo, rook);
            }
        }

        this.board.set(move.to, { color: piece.color, type: move.promotion || piece.type });
        this.updateCastlingRights(move, piece);
        this.ep = piece.type === "p" && Math.abs(toRank - fromRank) === 2
            ? coordsToSquare(fromFile, (fromRank + toRank) / 2)
            : null;
        this.halfmove = piece.type === "p" || move.captured ? 0 : this.halfmove + 1;
        if (this.side === "b") this.fullmove += 1;
        this.side = opposite(this.side);
    }

    updateCastlingRights(move, piece) {
        const remove = (chars) => {
            for (const char of chars) this.castling = this.castling.replace(char, "");
        };
        if (piece.type === "k") remove(piece.color === "w" ? "KQ" : "kq");
        if (piece.type === "r") {
            if (move.from === "a1") remove("Q");
            if (move.from === "h1") remove("K");
            if (move.from === "a8") remove("q");
            if (move.from === "h8") remove("k");
        }
        if (move.to === "a1") remove("Q");
        if (move.to === "h1") remove("K");
        if (move.to === "a8") remove("q");
        if (move.to === "h8") remove("k");
    }

    inCheck(color) {
        const kingSquare = Array.from(this.board.entries())
            .find(([, piece]) => piece.color === color && piece.type === "k")?.[0];
        return kingSquare ? this.isSquareAttacked(kingSquare, opposite(color)) : false;
    }

    isSquareAttacked(square, byColor) {
        const [file, rank] = squareToCoords(square);
        const pawnRank = byColor === "w" ? rank - 1 : rank + 1;
        for (const df of [-1, 1]) {
            const pawn = this.board.get(coordsToSquare(file + df, pawnRank));
            if (pawn?.color === byColor && pawn.type === "p") return true;
        }

        for (const [df, dr] of [[1, 2], [2, 1], [2, -1], [1, -2], [-1, -2], [-2, -1], [-2, 1], [-1, 2]]) {
            const piece = this.board.get(coordsToSquare(file + df, rank + dr));
            if (piece?.color === byColor && piece.type === "n") return true;
        }

        for (const [df, dr, types] of [
            [1, 0, "rq"], [-1, 0, "rq"], [0, 1, "rq"], [0, -1, "rq"],
            [1, 1, "bq"], [1, -1, "bq"], [-1, 1, "bq"], [-1, -1, "bq"]
        ]) {
            let nextFile = file + df;
            let nextRank = rank + dr;
            while (true) {
                const to = coordsToSquare(nextFile, nextRank);
                if (!to) break;
                const piece = this.board.get(to);
                if (piece) {
                    if (piece.color === byColor && types.includes(piece.type)) return true;
                    break;
                }
                nextFile += df;
                nextRank += dr;
            }
        }

        for (const [df, dr] of [[1, 1], [1, 0], [1, -1], [0, 1], [0, -1], [-1, 1], [-1, 0], [-1, -1]]) {
            const piece = this.board.get(coordsToSquare(file + df, rank + dr));
            if (piece?.color === byColor && piece.type === "k") return true;
        }
        return false;
    }
}

const statusEl = $("status");
const logEl = $("log");
let socket = null;
let connected = false;
let subscriptionId = 0;
let subscriptions = new Set();
let chess = new LocalChess();
let currentUserId = null;
let currentUsername = "";
let matchId = null;
let whitePlayerId = null;
let blackPlayerId = null;
let turnOwner = "WHITE";
let whiteTimeRemaining = WHITE_TIME_MS;
let blackTimeRemaining = WHITE_TIME_MS;
let turnStartTime = Date.now();
let winnerId = null;
let gameOver = false;
let isLocalGame = false;
let inQueue = false;
let isBoardFlipped = false;
let selectedSquare = null;
let legalMovesForSelected = [];
let lastMove = null;
let moveHistory = [];
let pendingMove = null;
let pendingDrawOffer = false;

let defaultBaseUrl = "http://localhost:8080";
if (window.location.origin && window.location.origin !== "null" && window.location.protocol !== "file:") {
    try {
        const url = new URL(window.location.origin);
        url.port = "8080";
        defaultBaseUrl = url.origin;
    } catch (e) {
        // fallback
    }
}
$("baseUrl").value = defaultBaseUrl;

function appendLog(message, data, type = "") {
    const entry = document.createElement("div");
    entry.className = "log-entry" + (type ? ` log-${type}` : "");
    const time = document.createElement("span");
    time.className = "log-time";
    time.textContent = `[${new Date().toLocaleTimeString()}] `;
    entry.appendChild(time);
    entry.appendChild(document.createTextNode(message));
    if (data !== undefined) {
        entry.appendChild(document.createTextNode("\n" + JSON.stringify(data, null, 2)));
    }
    logEl.appendChild(entry);
    logEl.scrollTop = logEl.scrollHeight;
}

function safeJson(text) {
    try {
        return text ? JSON.parse(text) : null;
    } catch {
        return text;
    }
}

function readJwtSubject(token) {
    try {
        const payload = token.split(".")[1];
        if (!payload) return null;
        const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
        const json = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "="));
        return JSON.parse(json).sub ?? null;
    } catch {
        return null;
    }
}

function syncIdentityFromToken() {
    if (currentUserId !== null) return;
    const id = readJwtSubject($("token").value.trim());
    if (id === null) return;
    currentUserId = id;
    currentUsername = currentUsername || "token";
    $("currentUserDisplay").innerHTML = `<span>User</span><strong>Player #${escapeText(String(id))}</strong>`;
}

function setStatus(text, state = "") {
    statusEl.className = `status ${state}`;
    statusEl.querySelector("span:last-child").textContent = text;
}

function setConnectedState(value) {
    connected = value;
    if (!value) inQueue = false;
    $("connectBtn").disabled = value;
    $("disconnectBtn").disabled = !value;
    $("joinBtn").disabled = !value || inQueue;
    $("cancelBtn").disabled = !value || !inQueue;
    $("reconnectBtn").disabled = !value;
    $("sendMoveBtn").disabled = !value && !isLocalGame;
    if (!inQueue) $("queueBanner").classList.remove("active");
    setStatus(value ? "Connected" : "Disconnected", value ? "connected" : "");
    updateGameUI();
}

function toWsUrl(baseUrl, token) {
    const base = new URL(baseUrl);
    base.protocol = base.protocol === "https:" ? "wss:" : "ws:";
    base.pathname = "/ws/websocket";
    base.search = "";
    base.searchParams.set("token", token);
    return base.toString();
}

function stompSend(command, headers = {}, body = "") {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        appendLog("Socket is not open", undefined, "error");
        return false;
    }
    const lines = [command];
    Object.entries(headers).forEach(([key, value]) => lines.push(`${key}:${value}`));
    socket.send(lines.join("\n") + "\n\n" + body + "\0");
    appendLog(`SEND ${command}`, { headers, body: safeJson(body) });
    return true;
}

function subscribe(destination) {
    if (subscriptions.has(destination)) return;
    if (stompSend("SUBSCRIBE", { id: `sub-${++subscriptionId}`, destination })) {
        subscriptions.add(destination);
        appendLog(`Subscribed ${destination}`, undefined, "success");
    }
}

function parseFrame(raw) {
    const [head, ...bodyParts] = raw.split("\n\n");
    const lines = head.split("\n").filter(Boolean);
    const command = lines.shift() || "";
    const headers = {};
    lines.forEach((line) => {
        const index = line.indexOf(":");
        if (index > -1) headers[line.slice(0, index)] = line.slice(index + 1);
    });
    return { command, headers, body: bodyParts.join("\n\n") };
}

function handleFrame(frame) {
    if (!frame.command) return;
    const body = typeof frame.body === "string" ? safeJson(frame.body) : frame.body;
    appendLog(`RECEIVE ${frame.command}`, { headers: frame.headers, body });

    if (frame.command === "CONNECTED") {
        setConnectedState(true);
        subscribe("/user/sub/queue");
        stompSend("SEND", { destination: "/app/matchmaking/reconnect" });
        return;
    }

    if (frame.command === "ERROR") {
        setStatus("STOMP error", "error");
        return;
    }

    if (frame.command !== "MESSAGE" || !body || typeof body !== "object") return;
    if (body.matchId && body.fen) {
        loadMatch(body);
    } else if (body.type === "MOVE") {
        handleMoveBroadcast(body);
    } else if (body.type === "DRAW_OFFER") {
        handleDrawOffer(body);
    } else if (body.type === "END") {
        handleMatchEnd(body);
    }
}

async function auth(path) {
    let baseUrl = $("baseUrl").value.trim();
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
        baseUrl = "http://" + baseUrl;
    }
    try {
        const url = new URL(baseUrl);
        url.port = "8080";
        baseUrl = url.origin;
        $("baseUrl").value = baseUrl; // update UI
    } catch (e) {
        baseUrl = "http://localhost:8080";
        $("baseUrl").value = baseUrl;
    }
    try {
        const response = await fetch(`${baseUrl}${path}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                username: $("username").value.trim(),
                password: $("password").value
            })
        });
        const text = await response.text();
        const payload = safeJson(text) || {};
        if (!response.ok) throw new Error(`${response.status} ${response.statusText}: ${text}`);

        $("token").value = payload.token || "";
        currentUserId = payload.id ?? null;
        currentUsername = payload.username || "";
        $("currentUserDisplay").innerHTML = `<span>User</span><strong>${escapeText(currentUsername)} #${escapeText(String(currentUserId))}</strong>`;
        appendLog(`${path} succeeded`, { id: currentUserId, username: currentUsername }, "success");
        updateGameUI();
    } catch (error) {
        $("currentUserDisplay").innerHTML = "<span>User</span><strong>Auth failed</strong>";
        appendLog(`${path} failed`, { message: error.message }, "error");
    }
}

function escapeText(value) {
    return value.replace(/[&<>"']/g, (char) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;"
    }[char]));
}

function connectSocket() {
    const token = $("token").value.trim();
    if (!token) {
        appendLog("Login or paste a JWT before connecting", undefined, "error");
        return;
    }
    syncIdentityFromToken();
    let baseUrl = $("baseUrl").value.trim();
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
        baseUrl = "http://" + baseUrl;
    }
    try {
        const url = new URL(baseUrl);
        url.port = "8080";
        baseUrl = url.origin;
        $("baseUrl").value = baseUrl;
    } catch (e) {
        baseUrl = "http://localhost:8080";
        $("baseUrl").value = baseUrl;
    }
    const wsUrl = toWsUrl(baseUrl, token);
    subscriptions = new Set();
    socket = new WebSocket(wsUrl);
    setStatus("Connecting", "connecting");
    appendLog("Opening WebSocket", { wsUrl });

    socket.onopen = () => {
        stompSend("CONNECT", {
            "accept-version": "1.2",
            "heart-beat": "10000,10000"
        });
    };
    socket.onmessage = (event) => {
        String(event.data)
            .split("\0")
            .filter((part) => part.trim().length > 0)
            .forEach((part) => handleFrame(parseFrame(part)));
    };
    socket.onerror = () => {
        setStatus("Socket error", "error");
        appendLog("WebSocket error", undefined, "error");
    };
    socket.onclose = (event) => {
        setConnectedState(false);
        appendLog("WebSocket closed", { code: event.code, reason: event.reason || "" });
    };
}

function loadMatch(snapshot) {
    isLocalGame = false;
    matchId = snapshot.matchId;
    whitePlayerId = snapshot.whitePlayerId;
    blackPlayerId = snapshot.blackPlayerId;
    turnOwner = snapshot.turnOwner || "WHITE";
    whiteTimeRemaining = snapshot.whiteTimeRemaining || WHITE_TIME_MS;
    blackTimeRemaining = snapshot.blackTimeRemaining || WHITE_TIME_MS;
    turnStartTime = snapshot.turnStartTime || Date.now();
    winnerId = snapshot.winnerId ?? null;
    gameOver = winnerId !== null;
    pendingMove = null;
    pendingDrawOffer = false;
    selectedSquare = null;
    legalMovesForSelected = [];
    lastMove = null;
    moveHistory = parsePgnMoves(snapshot.pgn || "");
    chess = new LocalChess(snapshot.fen || START_FEN);
    inQueue = false;

    $("matchId").value = matchId;
    $("moveDestination").value = `/app/match/${matchId}/move`;
    $("queueBanner").classList.remove("active");
    $("drawAlertBanner").classList.remove("active");
    if (connected) subscribe(`/sub/match/${matchId}`);
    isBoardFlipped = sameId(currentUserId, blackPlayerId);
    appendLog("Match loaded", snapshot, "success");
    updateAll();
}

function parsePgnMoves(pgn) {
    return pgn.trim() ? pgn.trim().split(/\s+/) : [];
}

function startLocalGame() {
    isLocalGame = true;
    matchId = "local";
    whitePlayerId = "local-white";
    blackPlayerId = "local-black";
    turnOwner = "WHITE";
    whiteTimeRemaining = WHITE_TIME_MS;
    blackTimeRemaining = WHITE_TIME_MS;
    turnStartTime = Date.now();
    winnerId = null;
    gameOver = false;
    pendingMove = null;
    pendingDrawOffer = false;
    selectedSquare = null;
    legalMovesForSelected = [];
    lastMove = null;
    moveHistory = [];
    chess = new LocalChess();
    $("matchId").value = "local";
    $("moveDestination").value = "";
    appendLog("Local game started", undefined, "success");
    updateAll();
}

function handleMoveBroadcast(payload) {
    let skipLocalDeduction = false;
    if (payload.whiteTimeRemaining !== undefined) {
        whiteTimeRemaining = payload.whiteTimeRemaining;
        skipLocalDeduction = true;
    }
    if (payload.blackTimeRemaining !== undefined) {
        blackTimeRemaining = payload.blackTimeRemaining;
        skipLocalDeduction = true;
    }
    applyAcceptedMove(payload.move, payload.turnOwner, skipLocalDeduction);
}

function applyAcceptedMove(moveUci, nextTurnOwner, skipLocalDeduction = false) {
    const from = moveUci.slice(0, 2);
    const to = moveUci.slice(2, 4);
    const promotion = moveUci.slice(4) || undefined;

    if (!skipLocalDeduction) {
        const currentTurn = chess.turn();
        const elapsed = Math.max(0, Date.now() - turnStartTime);
        if (currentTurn === "w") {
            whiteTimeRemaining = Math.max(0, whiteTimeRemaining - elapsed);
        } else {
            blackTimeRemaining = Math.max(0, blackTimeRemaining - elapsed);
        }
    }

    const result = chess.move({ from, to, promotion });
    if (!result) {
        appendLog("Move could not be applied to local board", { move: moveUci, fen: chess.fen() }, "error");
        if (connected && !isLocalGame) stompSend("SEND", { destination: "/app/matchmaking/reconnect" });
        return false;
    }
    lastMove = { from, to };
    pendingMove = null;
    moveHistory.push(moveUci);
    turnOwner = nextTurnOwner || (chess.turn() === "w" ? "WHITE" : "BLACK");
    turnStartTime = Date.now();
    selectedSquare = null;
    legalMovesForSelected = [];
    appendLog(`Move applied ${moveUci}`, undefined, "success");
    updateAll();
    return true;
}

function handleDrawOffer(payload) {
    if (sameId(payload.offererId, currentUserId)) {
        pendingDrawOffer = false;
        $("drawBtn").textContent = "Offered";
        $("drawBtn").disabled = true;
        appendLog("Draw offer sent", payload);
    } else {
        pendingDrawOffer = true;
        $("drawAlertBanner").classList.add("active");
        $("drawBtn").textContent = "Accept";
        appendLog("Draw offer received", payload);
    }
    updateGameUI();
}

function handleMatchEnd(payload) {
    if (payload.move) {
        applyAcceptedMove(payload.move, undefined, true);
    }
    gameOver = true;
    winnerId = payload.winnerId ?? null;
    pendingDrawOffer = false;
    pendingMove = null;
    $("drawAlertBanner").classList.remove("active");
    appendLog("Match ended", payload, "success");
    updateGameUI(payload.reason || "Game over");
    renderBoard();
}

function sendChessMove(moveUci) {
    if (!moveUci || moveUci.length < 4) return;
    if (isLocalGame) {
        applyAcceptedMove(moveUci, chess.turn() === "w" ? "BLACK" : "WHITE");
        return;
    }
    if (!matchId || !connected) {
        appendLog("No connected live match for move", { move: moveUci }, "error");
        return;
    }
    pendingMove = moveUci;
    stompSend("SEND", {
        destination: `/app/match/${matchId}/move`,
        "content-type": "application/json"
    }, JSON.stringify({ move: moveUci }));
    updateGameUI();
}

function isPlayerTurn() {
    if (gameOver || pendingMove) return false;
    if (isLocalGame) return true;
    const color = playerColor();
    return color !== null && color === chess.turn();
}

function playerColor() {
    if (isLocalGame) return chess.turn();
    if (sameId(currentUserId, whitePlayerId)) return "w";
    if (sameId(currentUserId, blackPlayerId)) return "b";
    return null;
}

function canMovePiece(piece) {
    if (!piece || gameOver || pendingMove) return false;
    if (isLocalGame) return piece.color === chess.turn();
    const color = playerColor();
    return color === piece.color && color === chess.turn();
}

function handleSquareClick(square) {
    if (!matchId || gameOver || pendingMove) return;
    const clickedPiece = chess.get(square);
    if (selectedSquare && legalMovesForSelected.some((move) => move.to === square)) {
        const move = legalMovesForSelected.find((candidate) => candidate.to === square);
        sendChessMove(move.from + move.to + (move.promotion || ""));
        selectedSquare = null;
        legalMovesForSelected = [];
        renderBoard();
        return;
    }
    if (selectedSquare === square) {
        selectedSquare = null;
        legalMovesForSelected = [];
        renderBoard();
        return;
    }
    if (canMovePiece(clickedPiece)) {
        selectedSquare = square;
        legalMovesForSelected = chess.moves({ square, verbose: true });
        renderBoard();
        return;
    }
    selectedSquare = null;
    legalMovesForSelected = [];
    renderBoard();
}

function renderBoard() {
    const board = $("board");
    board.innerHTML = "";
    for (let row = 0; row < 8; row++) {
        const rank = isBoardFlipped ? row : 7 - row;
        for (let col = 0; col < 8; col++) {
            const file = isBoardFlipped ? 7 - col : col;
            const square = coordsToSquare(file, rank);
            const piece = chess.get(square);
            const squareEl = document.createElement("button");
            squareEl.type = "button";
            squareEl.className = `square ${((file + rank) % 2 === 0) ? "dark" : "light"}`;
            squareEl.dataset.square = square;
            squareEl.setAttribute("aria-label", square);
            if (selectedSquare === square) squareEl.classList.add("selected");
            if (lastMove && (lastMove.from === square || lastMove.to === square)) squareEl.classList.add("last");
            const legalMove = legalMovesForSelected.find((move) => move.to === square);
            if (legalMove) {
                squareEl.classList.add("legal");
                if (piece) squareEl.classList.add("capture");
            }
            if (piece) {
                const pieceEl = document.createElement("span");
                pieceEl.className = `piece ${piece.color === "w" ? "white" : "black"}`;
                pieceEl.textContent = PIECES[piece.color + piece.type];
                squareEl.appendChild(pieceEl);
            }
            if ((isBoardFlipped ? row === 7 : row === 7) || (isBoardFlipped ? col === 7 : col === 0)) {
                const coord = document.createElement("span");
                coord.className = "coord";
                coord.textContent = square;
                squareEl.appendChild(coord);
            }
            squareEl.addEventListener("click", () => handleSquareClick(square));
            board.appendChild(squareEl);
        }
    }
}

function updateMoveList() {
    const moveList = $("moveList");
    moveList.innerHTML = "";
    if (!moveHistory.length) {
        const empty = document.createElement("div");
        empty.className = "move-row";
        empty.style.gridTemplateColumns = "1fr";
        empty.textContent = "No moves yet";
        moveList.appendChild(empty);
        return;
    }
    for (let index = 0; index < moveHistory.length; index += 2) {
        const row = document.createElement("div");
        row.className = "move-row";
        row.innerHTML = `<span>${Math.floor(index / 2) + 1}.</span><span>${escapeText(moveHistory[index] || "")}</span><span>${escapeText(moveHistory[index + 1] || "")}</span>`;
        moveList.appendChild(row);
    }
}

function updateGameUI(endReason = "") {
    const hasLiveMatch = Boolean(matchId && !isLocalGame && !gameOver);
    $("joinBtn").disabled = !connected || inQueue || hasLiveMatch;
    $("cancelBtn").disabled = !connected || !inQueue;
    $("reconnectBtn").disabled = !connected;
    $("whiteIdText").textContent = whitePlayerId ?? "-";
    $("blackIdText").textContent = blackPlayerId ?? "-";
    $("sendMoveBtn").disabled = !isLocalGame && (!connected || !matchId);

    if (!matchId) {
        $("gameTurnIndicator").textContent = "Local board ready";
        $("gameSubStatus").textContent = "Ready";
        $("playerColorIndicator").className = "tag";
        $("playerColorIndicator").textContent = "No side";
        $("drawBtn").disabled = true;
        $("resignBtn").disabled = true;
        updatePlayerBars();
        return;
    }

    const side = playerColor();
    if (gameOver) {
        $("gameTurnIndicator").textContent = endReason || "Game over";
        $("gameSubStatus").textContent = winnerId ? `Winner #${winnerId}` : "Draw";
    } else if (pendingMove) {
        $("gameTurnIndicator").textContent = "Move pending";
        $("gameSubStatus").textContent = pendingMove;
    } else if (isLocalGame) {
        $("gameTurnIndicator").textContent = chess.turn() === "w" ? "White to move" : "Black to move";
        $("gameSubStatus").textContent = "Local two-player board";
    } else if (isPlayerTurn()) {
        $("gameTurnIndicator").textContent = "Your turn";
        $("gameSubStatus").textContent = `Move as ${side === "w" ? "White" : "Black"}`;
    } else {
        $("gameTurnIndicator").textContent = chess.turn() === "w" ? "White to move" : "Black to move";
        $("gameSubStatus").textContent = side ? "Waiting for opponent" : "Spectating";
    }

    const tag = $("playerColorIndicator");
    if (isLocalGame) {
        tag.className = "tag";
        tag.textContent = "Local";
    } else if (side === "w") {
        tag.className = "tag white";
        tag.textContent = "White";
    } else if (side === "b") {
        tag.className = "tag black";
        tag.textContent = "Black";
    } else {
        tag.className = "tag";
        tag.textContent = "Spectator";
    }

    const isPlayer = isLocalGame || sameId(currentUserId, whitePlayerId) || sameId(currentUserId, blackPlayerId);
    $("drawBtn").disabled = gameOver || !isPlayer || (!isLocalGame && !connected);
    $("resignBtn").disabled = gameOver || !isPlayer || (!isLocalGame && !connected);
    $("drawBtn").textContent = pendingDrawOffer ? "Accept" : "Draw";
    updatePlayerBars();
}

function updatePlayerBars() {
    const topColor = isBoardFlipped ? "w" : "b";
    const bottomColor = isBoardFlipped ? "b" : "w";
    setPlayerBar("top", topColor);
    setPlayerBar("bottom", bottomColor);
}

function setPlayerBar(position, color) {
    const isWhite = color === "w";
    const id = isWhite ? whitePlayerId : blackPlayerId;
    const active = chess.turn() === color && matchId && !gameOver;
    $(`${position}Player`).classList.toggle("active", Boolean(active));
    $(`${position}PlayerName`).textContent = isWhite ? "White" : "Black";
    $(`${position}PlayerMeta`).textContent = id ? `Player ${id}` : "Waiting";
    $(`${position}Timer`).textContent = formatTime(timeRemainingFor(color));
}

function timeRemainingFor(color) {
    let remaining = color === "w" ? whiteTimeRemaining : blackTimeRemaining;
    if (matchId && !gameOver && chess.turn() === color) {
        remaining -= Math.max(0, Date.now() - turnStartTime);
    }
    return Math.max(0, remaining);
}

function formatTime(ms) {
    const total = Math.ceil(ms / 1000);
    const minutes = Math.floor(total / 60);
    const seconds = total % 60;
    return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function updateAll() {
    updateGameUI();
    updateMoveList();
    renderBoard();
}

$("registerBtn").addEventListener("click", () => auth("/auth/register"));
$("loginBtn").addEventListener("click", () => auth("/auth/login"));
$("connectBtn").addEventListener("click", connectSocket);
$("disconnectBtn").addEventListener("click", () => {
    stompSend("DISCONNECT");
    socket?.close();
});
$("joinBtn").addEventListener("click", () => {
    if (stompSend("SEND", { destination: "/app/matchmaking/join" })) {
        inQueue = true;
        $("queueBanner").classList.add("active");
        $("joinBtn").disabled = true;
        $("cancelBtn").disabled = false;
        updateGameUI();
    }
});
$("cancelBtn").addEventListener("click", () => {
    if (!inQueue) return;
    stompSend("SEND", { destination: "/app/matchmaking/cancel" });
    inQueue = false;
    $("queueBanner").classList.remove("active");
    $("joinBtn").disabled = !connected;
    $("cancelBtn").disabled = true;
    updateGameUI();
});
$("cancelBannerBtn").addEventListener("click", () => $("cancelBtn").click());
$("reconnectBtn").addEventListener("click", () => {
    if (connected) stompSend("SEND", { destination: "/app/matchmaking/reconnect" });
});
$("localBtn").addEventListener("click", startLocalGame);
$("flipBtn").addEventListener("click", () => {
    isBoardFlipped = !isBoardFlipped;
    renderBoard();
    updatePlayerBars();
});
$("drawBtn").addEventListener("click", () => {
    if (isLocalGame) {
        handleMatchEnd({ type: "END", reason: "DRAW", winnerId: null });
    } else if (matchId) {
        stompSend("SEND", { destination: `/app/match/${matchId}/draw` });
    }
});
$("acceptDrawAlertBtn").addEventListener("click", () => {
    if (matchId) stompSend("SEND", { destination: `/app/match/${matchId}/draw` });
});
$("resignBtn").addEventListener("click", () => {
    if (isLocalGame) {
        winnerId = chess.turn() === "w" ? blackPlayerId : whitePlayerId;
        handleMatchEnd({ type: "END", reason: "RESIGN", winnerId });
    } else if (matchId && window.confirm("Resign this match?")) {
        stompSend("SEND", { destination: `/app/match/${matchId}/resign` });
    }
});
$("sendMoveBtn").addEventListener("click", () => {
    const move = $("move").value.trim();
    if (isLocalGame) {
        sendChessMove(move);
        return;
    }
    const destination = $("moveDestination").value.trim() || `/app/match/${matchId}/move`;
    stompSend("SEND", { destination, "content-type": "application/json" }, JSON.stringify({ move }));
});
$("clearBtn").addEventListener("click", () => {
    logEl.textContent = "";
});

window.__myChess760 = {
    LocalChess,
    startLocalGame,
    loadMatch,
    handleFrame,
    handleSquareClick,
    state: () => ({
        fen: chess.fen(),
        matchId,
        isLocalGame,
        moveHistory: [...moveHistory],
        turn: chess.turn(),
        selectedSquare,
        legalMoves: legalMovesForSelected.map((move) => move.to),
        gameOver
    })
};

updateAll();
setInterval(updatePlayerBars, 1000);

if (new URLSearchParams(window.location.search).get("smoke") === "1") {
    startLocalGame();
    handleSquareClick("e2");
    handleSquareClick("e4");
    handleSquareClick("e7");
    handleSquareClick("e5");
    document.documentElement.dataset.smoke = moveHistory.join(",");
    document.documentElement.dataset.fen = chess.fen();
    document.documentElement.dataset.turn = chess.turn();
}

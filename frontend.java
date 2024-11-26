<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MeetAI - Virtual Meeting Room</title>
    <style>
        #video-container {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }
        video {
            width: 200px;
            height: 150px;
            background-color: #000;
        }
        #chat {
            position: fixed;
            bottom: 10px;
            right: 10px;
            width: 300px;
            height: 400px;
            border: 1px solid #ccc;
            background: #f9f9f9;
            padding: 10px;
        }
        #chat-input {
            width: 100%;
            padding: 10px;
        }
    </style>
</head>
<body>
    <h2>Welcome to MeetAI - Virtual Meeting Room</h2>
    <h3>Chatbot: Ask me anything!</h3>

    <div id="video-container"></div>

    <div id="chat">
        <div id="messages"></div>
        <input type="text" id="chat-input" placeholder="Ask me anything...">
        <button onclick="askChatbot()">Ask</button>
    </div>

    <script src="/socket.io/socket.io.js"></script>
    <script>
        const socket = io();
        const username = new URLSearchParams(window.location.search).get('username');
        const videoContainer = document.getElementById('video-container');
        const chatMessages = document.getElementById('messages');
        
        const peerConnections = {};
        const localVideo = document.createElement('video');
        document.body.appendChild(localVideo);
        localVideo.muted = true;

        const constraints = { video: true, audio: true };

        navigator.mediaDevices.getUserMedia(constraints)
            .then(stream => {
                localVideo.srcObject = stream;
                localVideo.play();

                socket.emit('signal', { type: 'new-user', username });

                socket.on('signal', (data) => {
                    if (data.type === 'offer') {
                        handleOffer(data);
                    } else if (data.type === 'answer') {
                        handleAnswer(data);
                    } else if (data.type === 'ice-candidate') {
                        handleIceCandidate(data);
                    }
                });

                socket.on('new-user', (data) => {
                    const peerConnection = new RTCPeerConnection();
                    peerConnections[data.username] = peerConnection;

                    stream.getTracks().forEach(track => peerConnection.addTrack(track, stream));

                    peerConnection.createOffer()
                        .then(offer => peerConnection.setLocalDescription(offer))
                        .then(() => socket.emit('signal', { type: 'offer', offer, username, to: data.username }));

                    peerConnection.ontrack = (event) => {
                        const remoteVideo = document.createElement('video');
                        remoteVideo.srcObject = event.streams[0];
                        remoteVideo.play();
                        videoContainer.appendChild(remoteVideo);
                    };

                    peerConnection.onicecandidate = (event) => {
                        if (event.candidate) {
                            socket.emit('signal', { type: 'ice-candidate', candidate: event.candidate, to: data.username });
                        }
                    };
                });
            });

        function handleOffer(data) {
            const peerConnection = new RTCPeerConnection();
            peerConnections[data.username] = peerConnection;

            peerConnection.setRemoteDescription(new RTCSessionDescription(data.offer))
                .then(() => peerConnection.createAnswer())
                .then(answer => peerConnection.setLocalDescription(answer))
                .then(() => socket.emit('signal', { type: 'answer', answer, to: data.username }));

            peerConnection.ontrack = (event) => {
                const remoteVideo = document.createElement('video');
                remoteVideo.srcObject = event.streams[0];
                remoteVideo.play();
                videoContainer.appendChild(remoteVideo);
            };

            peerConnection.onicecandidate = (event) => {
                if (event.candidate) {
                    socket.emit('signal', { type: 'ice-candidate', candidate: event.candidate, to: data.username });
                }
            };
        }

        function handleAnswer(data) {
            const peerConnection = peerConnections[data.username];
            peerConnection.setRemoteDescription(new RTCSessionDescription(data.answer));
        }

        function handleIceCandidate(data) {
            const peerConnection = peerConnections[data.username];
            peerConnection.addIceCandidate(new RTCIceCandidate(data.candidate));
        }

        function askChatbot() {
            const question = document.getElementById('chat-input').value;
            fetch('/ask-chatbot', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ question })
            })
            .then(response => response.json())
            .then(data => {
                const message = document.createElement('div');
                message.textContent = "Chatbot: " + data.answer;
                chatMessages.appendChild(message);
            });
        }

        socket.on('chat-message', function(msg) {
            const message = document.createElement('div');
            message.textContent = msg;
            chatMessages.appendChild(message);
        });
    </script>
</body>
</html>

## MVP

- Implement receiver from server via FastAPI, with port 8000
- Receive 5 signal from Server as JSON file.
- Have a setting button for manually type Server URL and Port.
- Signal will perform following tasks:
    - `flash`: Turn on flash on android
    - `cam`: Turn on camera, no need to send picture back to server.
    - `record`: Open Voice Recording and start recording, no need to send back to server.
    - `timer`: Set timer for a given duration.
    - `non-op function`: Create sinewave 440hz for 0.5ms twice (inherently a beep-beep audio file) and play it 

## Design

Simple, straight forward.

Show status is connected to server or not. Display which command does app received from server.

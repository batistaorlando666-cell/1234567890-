# ScreenCapture Sender V1

This folder contains the Android app scaffold and a tiny Flask test receiver.

## Android build
- Kotlin 2.1.21
- AGP 8.11.0
- Gradle 8.13
- minSdk 26

## Test server
Run:
```bash
cd server_test
pip install -r requirements.txt
python server_test.py
```
Then set the app URL to:
`http://YOUR_SERVER_IP:8000/upload`

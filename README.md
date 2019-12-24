# Android Websocket ASR Client

* Finite State Machine(서버 통신 음성 인식 상태 구분)
* Asynchronous ASR(음성 인식 각 상태별 시작과 끝 비동기의 깔끔한 처리)
* Websocket event listener(안드로이드 웹소켓 기반 음성 인식 이벤트 리스너 인터페이스 구성)
    1. onReady (녹음 준비 완료)
    2. onRecord (녹음 중 데이터 버퍼 생성 시마다)
    3. onPartialResult (음성 인식 중간 결과)
    4. onResult (인식 최종 결과)
    5. onRecordStop (인식 종료)
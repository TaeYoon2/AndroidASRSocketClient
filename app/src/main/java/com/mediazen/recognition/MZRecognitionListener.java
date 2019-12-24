package com.mediazen.recognition;

public interface MZRecognitionListener {

    // 녹음 준비가 끝나면
    public void onReady();
    // 녹음 중에 버퍼가 생성될 때
    public void onRecord(byte[] buffer);
    // 인식 중간 결과
    public void onPartialResult(String partialResult);
    // 인식 최종 결과
    public void onResult(ReconitionResult result);
    // 종료
    public void onRecordStop();
}

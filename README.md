**Prompt-Enhanced AI Chatbot Platform**

Kotlin(Android) · OkHttp3 · OpenAI API · Firebase Auth · Firestore
사용자 인증, 개인화된 대화 관리, 프롬프트 엔지니어링 기반 품질 개선을 통합한 End-to-End AI 챗봇 애플리케이션

[프로젝트 개요]

이 프로젝트는 모바일앱프로그래밍 실습에서 진행된 1인 개발 프로젝트로,
사용자 로그인/회원가입, OpenAI GPT 연동, 프롬프트 엔지니어링 기반 응답 품질 개선,
그리고 Firestore DB를 통한 대화 저장·조회·삭제 기능을 제공하는 엔드투엔드 AI 챗봇 플랫폼입니다.

[주요 기능]
1. 사용자 인증 (Firebase Auth)

이메일 회원가입 & 로그인

Google 계정 로그인/로그아웃

<p align="center"> <img src="screenshots/login.png" alt="로그인 화면" width="300"/> </p>

2. 챗봇 대화 (OpenAI API)

OpenAI GPT(gpt-3.5-turbo) API 연동

RecyclerView 기반 실시간 대화 UI

3. 프롬프트 엔지니어링 (Question Refinement)

단순 입력 대신, **“도메인 식별 → 핵심 의도 추출 → 전문가 수준 질문 재작성”**의 멀티스텝 프롬프트 구조 적용

예시:

입력: “I want to know about Djikstra”

Refinement → “What is Dijkstra's algorithm and how does it work?”

4. 대화 저장/조회/삭제 (Firestore DB)

SAVE → Firestore에 userId + timestamp 기반으로 저장

LIST → 사용자별 대화 목록 불러오기

CLEAR → Firestore 및 앱 화면에서 해당 대화 삭제

[성과 & 학습 포인트]

엔드투엔드 플랫폼 구축 경험 : 클라이언트–API–DB까지 직접 연결

프롬프트 엔지니어링 적용 : 단순 챗봇과 차별화된 “질문 품질 향상” 구현

실무형 설계 경험 : 사용자 인증, 데이터 영속성, 삭제 기능까지 반영

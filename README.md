# 이북 다운로드 앱 (ebook-drive-app)

저사양 안드로이드 이북뷰어용 **다운로드 전용** 앱.
- 사이트: https://ebookdrive.vercel.app  (PC 업로드: /upload)
- 4자리 코드로 목록 조회 → 파일 탭 → 안드로이드 Download 폴더에 저장 → 이북뷰어가 읽음
- 순수 Java + 프레임워크 위젯만 (AndroidX/Kotlin 없음), minSdk 19

## 설치 (이북 단말 브라우저에서)
최신 APK: https://github.com/drgant/ebook-drive-app/releases/latest/download/ebook-drive.apk
(설정에서 "출처를 알 수 없는 앱 설치" 허용 필요. 디버그 서명 APK입니다.)

## 빌드
`main` 브랜치에 푸시하면 GitHub Actions가 APK를 빌드해 `latest` 릴리스에 올립니다.

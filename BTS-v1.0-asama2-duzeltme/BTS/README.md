# BTS TECHNIC PLAYER v1.0
Android Studio (Koala+) ile aç: File > Open > bu klasör. Gradle senkronize edilince Run.
Paket: com.btstechnic.player · minSdk 26 · Kotlin · Media3 1.3.1

## Aşama 1 (bitti)
Açılış animasyonu + ses · tek seferlik giriş (şifreli oturum) · bağlantı testi · ana sayfa (3 bölme + 3D ayarlar)

## Aşama 2 (bu sürüm)
- Liste ekranı: Canlı TV / Diziler / Filmler, arama, kategori çipleri
- YouTube tarzı oynatıcı:
  - Dokun: kontrolleri göster/gizle · çift dokun sol/sağ: ±10 sn (arka arkaya dokundukça toplanır) · çift dokun orta: oynat/duraklat
  - Sol yarıda dikey kaydır: parlaklık · sağ yarıda: ses
  - Tam ekran düğmesi: dikey (16:9 + sıradakiler) ⇄ yatay tam ekran; cihaz dönünce kilit otomatik açılır
  - Ayarlar: oynatma hızı, kalite, görüntü oranı (orijinal / doldur / esnet)
  - Önceki/sonraki, ilerleme çubuğu, canlı rozeti, hata + "Yeniden dene", canlıda otomatik yeniden bağlanma (3 deneme)
  - Film/dizide kaldığın yerden devam, küçük pencere (PiP), uzaktan kumanda tuşları
  - M3U'daki Referer / User-Agent satırları desteklenir

## Sonraki aşama
Favoriler · dizi bölüm gruplama · kanal logoları · dosyadan M3U seçme · EPG

## APK üretme
**A) Android Studio:** Build > Build Bundle(s) / APK(s) > Build APK(s). Dosya: app/build/outputs/apk/debug/app-debug.apk
**B) Bilgisayarsız (GitHub):** klasörü yeni bir GitHub deposuna yükle → Actions sekmesi → "APK derle" çalışır → bitince "BTS-v1.0-apk" çıktısını indir (içinde app-debug.apk).
APK'yı telefona atınca "bilinmeyen kaynaklardan yüklemeye izin ver" gerekir.

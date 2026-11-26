# Barkod Satış

Android için basit barkodlu satış uygulaması (Kotlin + Jetpack Compose).

- Kamera (ML Kit) veya USB/Bluetooth barkod okuyucu ile ürün okutma
- 1-9 adet tuşları, sepet, satır bazında iskonto oranı ve indirim tutarı
- Yalnızca Nakit / Kredi Kartı ödeme, yazıcı çıktısı yok
- Bulunamayan barkod için elle ürün ekleme
- Excel (.xlsx) veya CSV'den ürün aktarma. Başlıklar: `Barkod`, `Ürün Adı`, `Satış İskontosu`, `Fiyatı`
- "Mali değeri yoktur" ibareli PDF fişi e-postayla gönderme
- İnternetsiz çalışır, veriler telefonda saklanır

## APK

`main` dalına her gönderimde GitHub Actions APK'yı derler ve **Releases** sayfasına koyar.
APK'yı telefona indirip kurun (ilk kurulumda "bilinmeyen kaynaklardan yüklemeye" izin vermeniz gerekir).

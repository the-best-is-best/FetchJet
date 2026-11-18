## 1.5.0'daki yenilikler

**Arşivler**
- **İndirmeden sonra çıkar:** ZIP, TAR, GZ ve XZ dosyaları bittiğinde kendiliğinden ya da satırdaki yeni Çıkar düğmesiyle çıkarılabilir. Masaüstünde 7-Zip kuruluysa 7Z, RAR, bölünmüş arşivler (.001, .part1.rar, .z01) ve parolalı arşivler de çalışır; FetchJet parolayı sorar.
- **Dosyalar nereye gider:** arşivin adını taşıyan bir klasöre, her zaman bir klasöre ya da arşivin yanına. Arşivi çıkarıldıktan sonra Geri Dönüşüm Kutusu'na da taşıyabilirsiniz.

**İndirmeler**
- **Bir indirmede sağ tık menüsü** (telefonlarda uzun basma): aç, klasörü aç, bağlantıyı kopyala, duraklat, devam et, sil ve daha fazlası.
- **Siteye göre klasör:** bir siteden ya da aynı şekilde başlayan bağlantılardan (bir kurs, bir proje) gelen her şeyi seçtiğiniz klasöre gönderin. Tarayıcıdan gönderilen bir kurs da buna uyar.
- **Silinen dosyalar Geri Dönüşüm Kutusu'na gider** (macOS ve Linux'ta Çöp Sepeti), kaybolmaz. Ayarlar'dan kapatabilirsiniz.
- **İndirmeden sonra tara:** Windows biten dosyayı antivirüsünüzle denetler ve antivirüsün engellediği bir indirme net bir iletiyle hemen durur. macOS'ta indirilen ve çıkarılan dosyalar alışılmış "internetten indirildi" işaretini taşır.

**Ağ (masaüstü)**
- **Kendi DNS'iniz:** FetchJet'in indirmeleri için bir DNS sunucusu seçin (DNS over HTTPS ya da düz bir DNS sunucusu) ve hangi ağ bağdaştırıcısını kullanacaklarını belirleyin. Bilgisayarın geri kalanı değil, yalnızca FetchJet'in indirmeleri etkilenir.

**Windows**
- **Yeni yükleyiciler:** bir Setup.exe ve masaüstü ile Başlat menüsü kısayolunu sizin seçtiğiniz yeni bir MSI. Bu sürümün, daha yeni ya da daha eski bir sürümün zaten kurulu olup olmadığını söyler ve sonunda FetchJet'i başlatabilir.
- **Taşınabilir sürüm:** kurulum gerektirmeden çalışan, verilerini yanındaki bir klasörde tutan bir .zip.
- **Daha yeni ffmpeg** Windows ve Linux'ta (videoyu ve sesi birleştiren araç). FetchJet bir sonraki açılışta kendiliğinden günceller.

**Android**
- **Daha küçük uygulama:** çoğu telefonda 83 MB yerine yaklaşık 53 MB ve kurulduktan sonra daha az yer kaplar.

**iPhone ve iPad**
- **Giriş gerektiren sayfalar, örneğin Zoom kayıtları:** sayfayı FetchJet içinde açın, şifreyi bir kez yazın ve sayfanın oturumuyla indirin. Safari'deki sayfa düğmesinde de "Bu sayfayı FetchJet'te aç" var.

**İleri düzey kullanıcılar için**
- **Komut satırı:** `FetchJet status`, `pause`, `start`, `remove` ve `delete` çalışan bir FetchJet'i yönetir. `FetchJet automation on` komutunu çalıştırana kadar kapalıdır.

**Tarayıcı eklentisi (3.5)**
- Listelediğiniz dosya türleriyle sınırlamadığınız sürece tarayıcıdaki her indirmeyi alır.
- Zoom'un yalnızca ses içeren kayıtlarında bir indirme düğmesi.
- Çerçeve içindeki oynatıcılar videolarını daha güvenilir şekilde bulur.

**Gizlilik**
- FetchJet artık kaç kişinin kullandığını bilmemiz için günde bir anonim ileti gönderir: bu kurulum için rastgele bir sayı, uygulama sürümü ve sistem; sunucu ülkeyi bağlantıdan okur. Bağlantı, dosya ya da kişisel veri yok. Toplamlar README'den bağlantı verilen herkese açık bir sayfadadır.

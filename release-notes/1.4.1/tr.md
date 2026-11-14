## 1.4.1'deki yenilikler

**İndirmeler**
- **FTP ve FTPS indirmeleri (yeni):** ftp://, ftps:// veya ftpes:// ile başlayan bağlantılar artık diğer doğrudan bağlantılar gibi iniyor: birkaç parça hâlinde ve sunucu izin veriyorsa kaldığı yerden devam ederek. Giriş bilgilerini bağlantıya yazın, Ayarlar > Ağ > Site girişleri altına kaydedin ya da anonim sunucular için boş bırakın. Bilmeniz gerekenler: FTP bağlantıları proxy üzerinden geçmez; masaüstü uygulamalarında TLS üzerinden FTP, sistemin curl'ü ile alınır; TLS üzerinden FTP henüz iPhone ve iPad'de yok.
- **Sunucuda değişen dosya:** indirme sırasında dosya sunucuda değiştirilirse FetchJet bunu fark eder. İndirme, karışık ve bozuk bir dosya kaydetmek yerine net bir iletiyle durur ve Devam et yeni sürümü indirir.
- **Sonda bekleme yok:** indirmenin sonu artık tek bir yavaş bağlantıyı beklemiyor. Sonda sürünen bağlantı yenisiyle değiştirilir.
- **Kaydetmeden önce kontroller:** Ekle penceresine bir sağlama toplamı (SHA-256 veya MD5) yapıştırın ya da sunucunun kendi sağlama toplamı başlığının karar vermesine izin verin. Eşleşmeyen dosya kaydedilmez. ZIP dosyaları girdi girdi denetlenir ve hasarlı bir parça bir kez yeniden indirilir.
- **Sunucu sınırları hatırlanır:** yalnızca birkaç bağlantıya izin veren site bir gün boyunca hatırlanır; böylece sonraki indirme, reddettiği sayıda bağlantıyla başlamaz. 403'ten sonra FetchJet IPv4 üzerinden yeniden deneyebilir (Windows, Android ve Linux).
- **Bağlantıyı sına:** Ekle penceresindeki bir düğme, bağlantıyı indirmeden çalışıp çalışmadığını, boyutunu ve kaldığı yerden devam edip edemeyeceğini söyler.
- **Dosya türüne göre klasör:** video, müzik veya arşivler için seçtiğiniz klasör yalnızca o tür için hatırlanır.
- **Listeden oynat:** biten bir video veya ses dosyasında artık dosyayı oynatıcınızda açan bir ▶ düğmesi görünür.
- **Uydurma dosya adı yok:** dosya adı olmayan bir bağlantıda «Dosya adı» kutusu boş görünür ve başlatma, siz bir ad yazana kadar bekler.
- **Ekranınıza sığar:** masaüstü penceresi artık kısmen görev çubuğunun altında değil, görünen ekranın içinde açılır.

**Torrentler**
- **Ayrıntılar:** paylaşım oranı, yüklenen miktar, parçalar ve parça boyutu, durumlarıyla izleyiciler, eşler, "Yeniden denetle", magnet bağlantısını kopyala ve videoları önizlemek için "önce ilk ve son parçalar".
- Çalışan bir torrente **izleyici ekle** ve seçtiğiniz **oranda paylaşımı durdur** (Ayarlar > BitTorrent).
- **Daha güvenli:** dosyaları kendi klasörünün dışına yazılacak bir torrent reddedilir.
- **HTTP proxy arkasında:** yalnızca UDP izleyicisi olan torrentler orada eş bulamaz; Ayarlar > Ağ ve hata iletisi artık bunu söylüyor.

**Masaüstü**
- **Klavye:** ok tuşları, Shift, Home / End, Page Up / Down, Enter (Alt+Enter ayrıntıları açar veya kapatır), Ctrl+R / P / O / E / N / F / A, Delete, Esc ve kısayol listesi için F1. Arapça, Farsça ve Rusça klavyelerle de çalışırlar; her alanda kopyala / yapıştır da öyle.
- **Komut satırı:** `/d` bağlantı, `/p` klasör, `/f` dosya adı, `/a` Ekle penceresi olmadan duraklatılmış ekle, `/n` pencere olmadan ekle ve hemen başlat, `/s` bekleyen her şeyi başlat.
- **Hız sınırı yalnızca bilgisayarı kullandığınız sürece** ve **zamanlanmış indirmeler için bilgisayarı uyandırma** (Windows).
- **Küçük şeyler:** pencere başlığında sürüm, Esc Ekle penceresini kapatır, dosya türü rozetleri açık temada okunur ve Cihaza gönder başlığı her yerde çevrildi.

**Ses**
- **OGG:** bir YouTube (Opus) sesi için OGG seçmek artık yeniden kodlamıyor. Ses, özgün kalite ve boyutta hemen kaydedilir.
- **Sesi sıkıştır:** artık Dönüştür ile aynı biçimleri sunuyor (M4A, MP3, Opus, OGG, FLAC, WAV), dosya OGG ise OGG'yi koruyor ve tahmini boyut gerçeğe daha yakın.

**Tarayıcı eklentisi (3.4)**
- **Bağlantı:** tarayıcı henüz uygulamaya bağlı değilken eklenti sonsuza dek yüklenmek yerine bunu hemen söyler ("FetchJet'i açın ve İzin ver'e dokunun"). İzin ver'e dokununca kaliteler kendiliğinden görünür. Tarayıcı "FetchJet açılsın mı?" diye sorarsa sekme artık siz yanıtlayana kadar kalır. Ayarlar > Tarayıcı eklentisi içinde "Tüm eşlemeleri kaldır" var.
- **Tarayıcı, uygulama alana kadar indirmeyi saklar,** gönderdiğiniz formlar tarayıcıda kalır ve gizli pencereler kendi çerezlerini kullanır.
- **Bağlantılar:** bir dosya bağlantısı, sayfanın kendi tıklama kodu çalıştıktan sonra alınır; böylece eskiden 403 veren siteler artık çalışır. Sayfa indirme olarak işaretlemedikçe PDF bağlantısı tarayıcıda açılır. "Tüm bağlantıları indir" indirme olarak işaretlenmiş bağlantıları ve data-* özniteliklerinde tutulan bağlantıları da bulur; Google Drive'da "Özgün dosya" satırı vardır.
- **Video düğmesi:** klavyeyle çalışır (Tab, Enter), bir kenara sürüklediğinizde o kenara yapışır, üst üste binen videoların her grubu için bir düğme vardır ve sayfanın altında gizli bir videoda düğme yoktur. Bir sitede gizleyebilirsiniz; Telegram Web ise neden indirilemediğini açıklar.
- **Eklenti uygulamanın temasını izler:** açılır penceresi ve ayar sayfası, uygulama gibi açık veya koyudur (Sistem cihazı izler); uygulama çalışmıyorsa tarayıcı gibi.

**Güncellemeler**
- **Güncelleme penceresi** uygulamanın dilini izler ve sürüm notları çevrilmiştir.

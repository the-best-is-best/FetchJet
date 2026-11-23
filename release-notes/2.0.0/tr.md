## 2.0.0'daki yenilikler

**Daha akıllı Ekle penceresi**
- **FetchJet motoru bağlantıdan seçer:** bir dosya, bir magnet bağlantısı veya bilinen bir video sitesi kendiliğinden Direct, Torrent ya da Media'ya gider. Önce bağlantıyı bir kez denetler ("Bağlantı denetleniyor…"); Direct / Media seçimi yalnızca videolu bir sayfa ya da denetlenemeyen bir bağlantı için görünür.
- **Yazdığınız her harf için denetim yok:** denetim, yazmayı bıraktığınızda başlar (yapıştırılan bağlantıda hemen). Bağlantıyı değiştirmek eski okumayı iptal eder; böylece pencere, örneğin hiç kaynak bulamayan bir magnet ile "Ad bulunuyor…" üzerinde takılı kalmaz.

**Bu sayfadaki dosyaları bul**
- **Yeni:** bir sayfa bağlantısı yapıştırın ve "Bu sayfadaki dosyaları bul"u kullanın. FetchJet sayfanın dosyalarını listeler: görseller, bağlantılar ve düz metin olarak yazılmış adresler. Dosya adı olmayan bağlantılar için sunucudan gerçek adı ve boyutu sorar. İsterseniz sayfanın bağlandığı sayfaları da bir ya da iki seviye derine kadar okur.
- **Okurken sonuçlar:** sayfa okunurken dosyalar görünür, "Okumayı durdur" bulunanları korur.
- **Neyin ineceğini seçin:** dosya türleri, o dosyaları gizleyen ya da gösteren düğmelerdir; gizlediğiniz dosyalar indirilmez. Her satır türünü gösterir (ZIP, MP3, PNG…).
- **Grup olarak kaydedilir:** dosyalar sayfanın adını taşıyan bir klasöre ve gruba gider. Daha sonra "Yeni dosyaları denetle", zaten sahip olduklarınızı işaretler ve kilitler ("N daha önce indirildi").
- **GitHub sürüm sayfaları** tek bir istekle okunur.
- **Okunamayan sayfalar:** sitenin reddettiği sayfalar (bir denetim ya da engel) sayılır. Bağlantılarını JavaScript ile oluşturan sayfalar adlarıyla belirtilir; bunları tarayıcıda açıp eklentinin "Tüm bağlantıları indir" özelliğini kullanmanız önerilir.

**Oynatma listeleri ve altyazılar**
- **Oynatma listeleri:** daha önce indirdiğiniz videolar işaretlenir ve sayılır ("N daha önce indirildi"); grup olarak kaydedilen bir oynatma listesi yeni videolar için denetlenebilir.
- **Altyazılar:** "Videoya göm" veya "Ayrı dosya"yı seçin, ardından SRT ya da VTT. Diller tek tek istenir; sitenin bir an için reddettiği dil yeniden istenir. Tamamlanmış bir videoya da ayrıntılarından ya da satır menüsünden altyazı ekleyebilirsiniz.
- **"Cihaza gönder" altyazıları korur:** başka bir cihaza gönderilen tamamlanmış bir video orada da "Altyazı ekle" seçeneğini sunar, dosyada hangi dillerin zaten olduğunu bilir ve yanındaki altyazı dosyaları da onunla birlikte gider.

**Videolar**
- **Google Drive:** her Drive dosya bağlantısı doğrudan iner; sahibi indirmeyi kapatmış bir dosya bunu söyler.
- **Korumalı videolar:** DRM korumalı bir video, sonradan başarısız olmak yerine kalite listesinden önce öyle olduğu belirtilir.
- **Her yerde oynayan videolar:** bu anahtar (artık Ayarlar > Medya araçları içinde ve Android'de de var) her TV ve oynatıcının açtığı H.264 + AAC dosyalarını ya da daha küçük AV1 / VP9 dosyalarını seçer.

**Ayarlar**
- **Yeniden düzenlendi:** İndirmeler (konum, bağlantılar, kuyruk), yeni bir Hız sınırları sayfası (hazır ayarlar, boşta sınırı, zamanlama) ve yeni bir Dosyalar ve klasörler sayfası (türe göre sırala, klasöre göre grupla, çıkar, Geri Dönüşüm Kutusu, tarama, siteye göre klasörler).

**Hız**
- Büyük dosyalar varsayılan olarak 32'ye kadar bağlantı kullanır.
- 20 saniye boyunca veri alamayan bir istek bırakılıp yeniden istenir; böylece takılan tek bir bağlantı artık indirmeyi bir dakika durdurmaz.
- Kısa ve hızlı aktarımlarda yavaş bir bağlantı daha erken değiştirilir.
- macOS site başına 64 bağlantıya izin verir; aynı siteden ikinci bir indirme artık beklemez.

**Küçük şeyler**
- "İndirmeler bitince" artık ayıklanan bir arşivi ya da taranan bir dosyayı da bekler.
- Adı uzun bir torrent artık dosya kartını uzatmaz ve dosya satırları önce dosya adını gösterir.
- Bağlantılar ve klasör yolları Arapça ve Farsçada yönlerini korur.
- Android: arka planda okunan bir oynatma listesi artık uygulamayı çökertmez.
- Masaüstü penceresi gösterildikten ya da yeniden boyutlandırıldıktan sonra boyanmamış bölge kalmadan tamamen yeniden çizilir.
- macOS uygulaması macOS 12 ve sonrasında çalışacak şekilde derlenir.
- **Android: yeniden kurulum ya da yeni telefon sonrası bozuk liste yok:** indirme listesi artık yedekten geri yüklenmiyor, çünkü bağlantıları telefona kilitli ve başka yerde okunamıyordu. İndirdiğiniz dosyalar klasörünüzde kalır.
- **Daha net altyazı mesajı:** eski bir indirmenin bağlantısı okunamadığında "Altyazı ekle", videoda altyazı olmadığını söylemek yerine altyazıların okunamadığını söyler.

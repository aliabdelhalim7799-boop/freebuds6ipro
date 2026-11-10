# تقرير الاختبارات

## فُحص هنا (بيئة الإنشاء، بلا Android SDK ولا Gradle)
- لم يُبنَ المشروع ولم تُشغَّل Unit Tests. الكود كُتب لكنه **غير مُترجَم بعد**؛ أول بناء على GitHub Actions قد يكشف أخطاء ترجمة بسيطة.

## Unit Tests (تعمل على GitHub Actions، لا تحتاج جهازًا)
HighPass، NoiseSuppressor (ضجيج/إشارة عالية)، AGC (رفع + بوابة الصمت)، Compressor، Limiter (لا تجاوز للسقف)، Equalizer، كل Presets، Metrics، استقلال معدل العينة. الحالة: لم تُشغَّل بعد.

## قائمة التحقق على Infinix X6812 + FreeBuds 6i (تنفذها أنت)
| # | الاختبار | النتيجة |
|---|---|---|
| 1 | اكتشاف FreeBuds في تبويب البلوتوث | |
| 2 | فصل/إعادة اتصال ويتحدث التبويب | |
| 3 | تسجيل SCO: هل المصدر "Bluetooth SCO"؟ | |
| 4 | تسجيل بمايك الهاتف للمقارنة | |
| 5 | AGC/NS تشغيل وإيقاف وفرق A/B مسموع | |
| 6 | SNR/Floor قبل وبعد لكل Preset | |
| 7 | حفظ/استعادة/تصدير/استيراد Profile | |
| 8 | ANC Lab: Off/Cozy/General/Ultra يمين ويسار | |
| 9 | Transparency Lab: Off/Transparency/Voice | |
| 10 | بطارية وذاكرة أثناء تسجيل 8 ثوانٍ | |

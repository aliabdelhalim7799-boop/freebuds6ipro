package com.freebuds6ipro.ui

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freebuds6ipro.MainViewModel
import com.freebuds6ipro.audio.Effects
import com.freebuds6ipro.data.LabResult
import com.freebuds6ipro.dsp.DspSettings
import com.freebuds6ipro.dsp.Metrics
import java.util.Locale

fun tr(ar: String, en: String) = if (Locale.getDefault().language == "ar") ar else en

enum class St(val ar: String, val en: String, val c: Long) {
    SUPPORTED("مدعوم", "Supported", 0xFF2E7D32), UNSUPPORTED("غير مدعوم", "Unsupported", 0xFFC62828),
    EXPERIMENTAL("تجريبي", "Experimental", 0xFFEF6C00), CONNECTED("متصل", "Connected", 0xFF1565C0),
    NOT_CONNECTED("غير متصل", "Not connected", 0xFF757575)
}

@Composable fun Badge(s: St) = Surface(color = Color(s.c).copy(alpha = .15f), shape = RoundedCornerShape(8.dp)) {
    Text(tr(s.ar, s.en), color = Color(s.c), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp, 2.dp))
}
@Composable fun Sec(title: String, content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); content()
    }
}
@Composable fun Feature(title: String, st: St, note: String) = Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium); Badge(st)
    }
    Text(note, style = MaterialTheme.typography.bodySmall)
}
@Composable fun SliderRow(label: String, v: Float, r: ClosedFloatingPointRange<Float>, unit: String, on: (Float) -> Unit) = Column {
    Text("$label: ${"%.1f".format(v)} $unit", style = MaterialTheme.typography.bodySmall)
    Slider(v, on, valueRange = r)
}
@Composable fun ToggleRow(title: String, on: Boolean, ch: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
    Text(title, fontWeight = FontWeight.Medium); Switch(on, ch)
}
@Composable fun Chips(items: List<Pair<String, String>>, sel: String?, pick: (String) -> Unit) =
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (k, label) -> FilterChip(selected = sel == k, onClick = { pick(k) }, label = { Text(label) }) }
    }
@Composable fun LevelBar(db: Float) = LinearProgressIndicator(progress = { ((db + 90f) / 90f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())

@Composable fun App(vm: MainViewModel, needMic: (() -> Unit) -> Unit) {
    val tabs = listOf(tr("الرئيسية", "Home"), tr("البلوتوث", "Bluetooth"), tr("استوديو المايك", "Mic Studio"),
        tr("مختبر ANC", "ANC Lab"), tr("مختبر الشفافية", "Transparency"), tr("الملفات", "Profiles"), tr("التقرير", "Report"))
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("FreeBuds 6i Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vm.message?.let { m -> Card { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m, Modifier.weight(1f), color = MaterialTheme.colorScheme.error); TextButton({ vm.message = null }) { Text("OK") } } } }
            when (tab) {
                0 -> Home(vm); 1 -> Bluetooth(vm); 2 -> Mic(vm, needMic)
                3 -> Lab(vm, needMic, "anc", listOf("Off", "Cozy", "General", "Ultra"), true)
                4 -> Lab(vm, needMic, "tr", listOf("Off", "Transparency", "Voice"), false)
                5 -> Profiles(vm); else -> Report(vm)
            }
        }
    }
}

@Composable fun Home(vm: MainViewModel) {
    val b by vm.bt.state.collectAsState()
    val fb = b.devices.firstOrNull { it.isFreeBuds }
    Sec(tr("حالة الميزات (حقيقية)", "Feature status (real)")) {
        Feature(tr("اتصال السماعة", "Earbuds connection"), if (b.devices.isNotEmpty()) St.CONNECTED else St.NOT_CONNECTED,
            fb?.name ?: b.devices.firstOrNull()?.name ?: tr("لا توجد سماعة متصلة", "No headset connected"))
        Feature(tr("معالجة المايك داخل التطبيق", "In-app mic processing"), St.SUPPORTED,
            tr("HPF، AGC، ضاغط، تقليل ضوضاء، EQ، Limiter على تسجيلات التطبيق مع A/B.", "HPF, AGC, compressor, NS, EQ, limiter on in-app recordings with A/B."))
        Feature(tr("مايك السماعة عبر SCO", "Earbuds mic via SCO"), St.EXPERIMENTAL,
            tr("يعتمد على الجهاز والسماعة؛ تحقق منه في استوديو المايك (يعرض المصدر الفعلي).", "Device-dependent; verify in Mic Studio (shows the actual source)."))
        Feature(tr("تحسين مكالمات الهاتف", "Cellular call enhancement"), St.UNSUPPORTED,
            tr("Android لا يتيح لتطبيق خارجي التدخل في مسار صوت المكالمات.", "Android gives third-party apps no hook into the call audio path."))
        Feature(tr("تحسين WhatsApp / تطبيقات VoIP", "WhatsApp / VoIP enhancement"), St.UNSUPPORTED,
            tr("كل تطبيق يملك جلسة تسجيل خاصة به ولا يمكن ربط تأثير بها.", "Each app owns its recording session; effects cannot be attached from outside."))
        Feature(tr("مؤثرات النظام: NS / AGC / AEC", "System effects: NS / AGC / AEC"), if (Effects.ns() || Effects.agc() || Effects.aec()) St.SUPPORTED else St.UNSUPPORTED,
            "NS=${Effects.ns()}  AGC=${Effects.agc()}  AEC=${Effects.aec()}")
        Feature(tr("التحكم في ANC من التطبيق", "ANC control from app"), St.UNSUPPORTED,
            tr("لا يوجد API رسمي موثق. استخدم Huawei AI Life. التطبيق يقيس ويشخّص فقط.", "No documented API. Use Huawei AI Life. This app measures/diagnoses only."))
        Feature(tr("معالجة Transparency داخل السماعة", "Earbud-side Transparency DSP"), St.UNSUPPORTED, tr("قياس وإرشاد فقط.", "Measurement and guidance only."))
        Feature(tr("حفظ الإعدادات داخل السماعة", "Save settings in earbuds"), St.UNSUPPORTED, tr("الإعدادات محفوظة على الهاتف فقط.", "Settings are stored on the phone only."))
    }
}

@Composable fun Bluetooth(vm: MainViewModel) {
    val b by vm.bt.state.collectAsState()
    Sec(tr("حالة البلوتوث", "Bluetooth state")) {
        Text(tr("البلوتوث: ", "Bluetooth: ") + if (b.adapterOn) tr("يعمل", "on") else tr("متوقف", "off"))
        Text(tr("إذن الاتصال: ", "Permission: ") + if (b.permission) "OK" else tr("غير ممنوح", "not granted"))
        Text("SCO: " + if (b.scoConnected) tr("متصل (المايك يمر عبر السماعة)", "connected") else tr("غير نشط (طبيعي خارج المكالمة/التسجيل)", "inactive (normal outside call/recording)"))
        Text(tr("SCO متاح خارج المكالمة: ", "SCO available off-call: ") + b.scoAvailableOffCall)
        Button({ vm.bt.refresh() }) { Text(tr("تحديث", "Refresh")) }
    }
    Sec(tr("الأجهزة المتصلة", "Connected devices")) {
        if (b.devices.isEmpty()) Text(tr("لا توجد أجهزة صوت متصلة. يتحدث القسم تلقائيًا عند الاتصال/الفصل.", "No audio devices. Updates automatically on connect/disconnect."))
        b.devices.forEach { d ->
            Text(d.name + if (d.isFreeBuds) "  ✓ FreeBuds" else "", fontWeight = FontWeight.Medium)
            Text("HFP (calls/mic): ${if (d.hfp) "yes" else "no"}   A2DP (music): ${if (d.a2dp) "yes" else "no"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun Mic(vm: MainViewModel, needMic: (() -> Unit) -> Unit) {
    val s = vm.settings
    val names = mapOf("natural" to tr("صوت طبيعي", "Natural Voice"), "clear" to tr("صوت واضح", "Clear Voice"),
        "lowboost" to tr("رفع الصوت الخافت", "Low Voice Boost"), "noisy" to tr("بيئة صاخبة", "Noisy Environment"), "balanced" to tr("مكالمات متوازنة", "Balanced Calls"))
    Sec(tr("التسجيل والاختبار", "Record & test")) {
        Text(tr("المعالجة هنا على تسجيلات التطبيق فقط ولا تؤثر على المكالمات.", "Processing applies to in-app recordings only; it does not affect calls."), style = MaterialTheme.typography.bodySmall)
        ToggleRow(tr("استخدام مايك السماعة (SCO)", "Use earbuds mic (SCO)"), vm.viaSco) { vm.viaSco = it }
        Button({ needMic { vm.record() } }, enabled = !vm.busy && !vm.playing) { Text(if (vm.busy) tr("جارٍ التسجيل 8 ثوانٍ… تحدث الآن", "Recording 8 s… speak now") else tr("سجّل عينة 8 ثوانٍ", "Record 8 s sample")) }
        if (vm.busy) { Text(tr("🎙 الميكروفون قيد الاستخدام", "🎙 Microphone in use")); LevelBar(vm.level); Text("%.1f dBFS".format(vm.level)) }
        vm.route?.let { Text(tr("المصدر الفعلي للإدخال: ", "Actual input source: ") + it, fontWeight = FontWeight.Medium) }
        Text(tr("التسجيل يبقى في ذاكرة التطبيق ولا يُرفع ولا يُحفظ كملف.", "Recording stays in app memory; never uploaded or saved to a file."), style = MaterialTheme.typography.bodySmall)
    }
    Sec(tr("Presets", "Presets")) { Chips(names.toList(), vm.preset) { vm.applyPreset(it) } }
    Sec(tr("مقارنة A/B", "A/B comparison")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.play(false) }, enabled = vm.orig != null && !vm.playing && !vm.busy) { Text(tr("▶ الأصلي (A)", "▶ Original (A)")) }
            Button({ vm.play(true) }, enabled = vm.proc != null && !vm.playing && !vm.busy) { Text(tr("▶ المعالَج (B)", "▶ Processed (B)")) }
        }
        MetricsRow("A", vm.origM); MetricsRow("B", vm.procM)
        Text(tr("SNR تقدير تقريبي (مستوى الإطارات العالية − المنخفضة)، ليس قياسًا مخبريًا.", "SNR is a rough estimate (loud − quiet frames), not a lab measurement."), style = MaterialTheme.typography.bodySmall)
    }
    Sec(tr("خط المعالجة", "Processing chain")) {
        ToggleRow("High-pass", s.hpfOn) { vm.update(s.copy(hpfOn = it)) }
        if (s.hpfOn) SliderRow(tr("التردد", "Cutoff"), s.hpfHz, 40f..300f, "Hz") { vm.update(s.copy(hpfHz = it)) }
        ToggleRow(tr("تقليل الضوضاء + VAD", "Noise suppression + VAD"), s.nsOn) { vm.update(s.copy(nsOn = it)) }
        if (s.nsOn) SliderRow(tr("مقدار التقليل", "Reduction"), s.nsDb, 0f..30f, "dB") { vm.update(s.copy(nsDb = it)) }
        ToggleRow("AGC", s.agcOn) { vm.update(s.copy(agcOn = it)) }
        if (s.agcOn) { SliderRow(tr("المستوى المستهدف", "Target"), s.agcTargetDb, -35f..-10f, "dBFS") { vm.update(s.copy(agcTargetDb = it)) }
            SliderRow(tr("أقصى رفع", "Max gain"), s.agcMaxGainDb, 0f..24f, "dB") { vm.update(s.copy(agcMaxGainDb = it)) } }
        ToggleRow(tr("ضاغط", "Compressor"), s.compOn) { vm.update(s.copy(compOn = it)) }
        if (s.compOn) { SliderRow("Threshold", s.compThrDb, -40f..0f, "dB") { vm.update(s.copy(compThrDb = it)) }
            SliderRow("Ratio", s.compRatio, 1f..8f, ":1") { vm.update(s.copy(compRatio = it)) }
            SliderRow("Makeup", s.compMakeupDb, 0f..12f, "dB") { vm.update(s.copy(compMakeupDb = it)) } }
        ToggleRow(tr("وضوح الصوت (EQ)", "Voice clarity (EQ)"), s.eqOn) { vm.update(s.copy(eqOn = it)) }
        if (s.eqOn) { SliderRow(tr("Presence 3kHz", "Presence 3kHz"), s.presenceDb, -6f..9f, "dB") { vm.update(s.copy(presenceDb = it)) }
            SliderRow(tr("تقليل الاحتقان 300Hz", "Mud cut 300Hz"), s.mudDb, -9f..3f, "dB") { vm.update(s.copy(mudDb = it)) } }
        ToggleRow("Limiter", s.limOn) { vm.update(s.copy(limOn = it)) }
        if (s.limOn) SliderRow("Ceiling", s.limCeilDb, -6f..0f, "dBFS") { vm.update(s.copy(limCeilDb = it)) }
    }
}
@Composable fun MetricsRow(tag: String, m: Metrics.Result?) {
    if (m == null) { Text("$tag: —"); return }
    Text("$tag  RMS %.1f | Peak %.1f | Clip %d | Floor %.1f | SNR≈%.1f dB".format(m.rmsDb, m.peakDb, m.clipCount, m.noiseFloorDb, m.snrDb), style = MaterialTheme.typography.bodySmall)
}

fun labSummary(r: List<LabResult>): List<String> {
    val out = mutableListOf<String>()
    val avg = r.groupBy { it.mode }.mapValues { e -> e.value.map { it.hissDb }.average().toFloat() }
    val off = avg["Off"]
    avg.forEach { (m, v) -> out += if (off != null && m != "Off") "$m: %.1f dB (%+.1f vs Off)".format(v, v - off) else "$m: %.1f dB".format(v) }
    r.groupBy { it.mode }.forEach { (m, l) ->
        val a = l.filter { it.side == "L" }.map { it.hissDb }; val b = l.filter { it.side == "R" }.map { it.hissDb }
        if (a.isNotEmpty() && b.isNotEmpty()) out += tr("$m: الفرق بين اليسار واليمين %.1f dB".format(a.average() - b.average()), "$m: L−R difference %.1f dB".format(a.average() - b.average()))
    }
    val anc = avg.filterKeys { it != "Off" }
    if (anc.size >= 2) out += tr("أقل هسيس نسبيًا: ", "Lowest relative hiss: ") + anc.minByOrNull { it.value }!!.key
    return out
}

@Composable fun Lab(vm: MainViewModel, needMic: (() -> Unit) -> Unit, kind: String, modes: List<String>, sides: Boolean) {
    var mode by remember { mutableStateOf(modes.first()) }
    var side by remember { mutableStateOf("L") }
    val res = vm.labResults.filter { it.kind == kind }
    Sec(if (kind == "anc") tr("قياس هسيس ANC (نسبي)", "ANC hiss measurement (relative)") else tr("تحليل ضجيج Transparency", "Transparency noise analysis")) {
        Text(if (kind == "anc") tr("1) اجلس في غرفة هادئة. 2) فعّل الوضع من Huawei AI Life. 3) ضع السماعة ملاصقة لمايك الهاتف السفلي وأبقِ الوضع الصوتي ثابتًا. 4) اضغط قياس. كرر لكل وضع ولكل سماعة. الأرقام للمقارنة النسبية فقط وليست مستوى الأذن الفعلي.",
            "1) Quiet room. 2) Set the mode in Huawei AI Life. 3) Place the earbud against the phone's bottom mic, keep it still. 4) Measure. Repeat per mode and per side. Numbers are relative only, not true in-ear level.")
            else tr("قارن Off مع Transparency مع Voice Mode (من AI Life) في نفس المكان؛ ارتفاع نطاق الهسيس (>2kHz) يعني تضخيمًا إلكترونيًا للضجيج.",
            "Compare Off vs Transparency vs Voice Mode (set in AI Life) in the same place; a higher >2 kHz band means more electronic noise amplification."), style = MaterialTheme.typography.bodySmall)
        Chips(modes.map { it to it }, mode) { mode = it }
        if (sides) Chips(listOf("L" to tr("اليسار", "Left"), "R" to tr("اليمين", "Right")), side) { side = it }
        Button({ needMic { vm.measureLab(kind, mode, if (sides) side else "-") } }, enabled = !vm.busy) { Text(if (vm.busy) tr("جارٍ القياس 6 ثوانٍ… ابقَ هادئًا", "Measuring 6 s… stay quiet") else tr("قياس: $mode", "Measure: $mode")) }
        if (vm.busy) LevelBar(vm.level)
    }
    if (res.isNotEmpty()) {
        Sec(tr("النتائج المحفوظة", "Saved results")) {
            res.takeLast(12).forEach { Text("${it.mode} ${if (it.side != "-") it.side else ""}: floor %.1f dB | hiss-band %.1f dB".format(it.noiseFloorDb, it.hissDb), style = MaterialTheme.typography.bodySmall) }
            labSummary(res).forEach { Text("• $it", fontWeight = FontWeight.Medium) }
            Text(tr("إذا كان الهسيس مسموعًا لكن القياس لا يختلف عن Off، فالمصدر غالبًا داخل مسار الأذن ولا يلتقطه مايك الهاتف.", "If hiss is audible but the reading matches Off, the source is likely inside the ear path and not captured by the phone mic."), style = MaterialTheme.typography.bodySmall)
            TextButton({ vm.clearLab(kind) }) { Text(tr("مسح النتائج", "Clear results")) }
        }
    }
    Sec(tr("ما يمكنك فعله فعليًا", "What you can actually do")) {
        Text(if (kind == "anc") tr("• حدّث Firmware السماعة من AI Life.\n• جرّب أحجام الأطراف المطاطية وتحقق من الإحكام (اختبار Ear Tip Fit في AI Life).\n• قارن Cozy وGeneral وUltra؛ التطبيق لا يغيّر ANC.\n• إن بقي الهسيس واضحًا في السماعتين فاحتفظ بتقرير التشخيص واعرضه على الدعم/الضمان.",
            "• Update earbud firmware in AI Life.\n• Try other ear tips and run Ear Tip Fit test in AI Life.\n• Compare Cozy/General/Ultra; this app cannot change ANC.\n• If hiss persists in both buds, keep the diagnostic report for warranty support.")
        else tr("• جرّب Voice Mode وخفّض مستوى الإدراك من AI Life.\n• هذا التطبيق لا يعالج صوت الشفافية.", "• Try Voice Mode and lower awareness level in AI Life.\n• This app does not process transparency audio."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun Profiles(vm: MainViewModel) {
    var name by remember { mutableStateOf("") }
    var imp by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    fun share(t: String) = ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
    Sec(tr("حفظ الإعدادات الحالية", "Save current settings")) {
        Chips(listOf("Call", "ANC", "Transparency", "Music", "Custom").map { it to it }, name) { name = it }
        OutlinedTextField(name, { name = it }, label = { Text(tr("اسم الملف", "Profile name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button({ vm.saveProfile(name) }, enabled = name.isNotBlank()) { Text(tr("حفظ", "Save")) }
        Text(tr("الملفات محفوظة على الهاتف فقط، وليست داخل ذاكرة السماعة.", "Profiles are stored on the phone only, not in the earbuds."), style = MaterialTheme.typography.bodySmall)
    }
    Sec(tr("الملفات المحفوظة", "Saved profiles")) {
        if (vm.profiles.isEmpty()) Text("—")
        vm.profiles.forEach { p ->
            Text(p, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ vm.loadProfile(p) }) { Text(tr("تحميل", "Load")) }
                OutlinedButton({ share(vm.export(p)) }) { Text(tr("تصدير", "Export")) }
                TextButton({ vm.deleteProfile(p) }) { Text(tr("حذف", "Delete")) }
            }
        }
    }
    Sec(tr("استيراد / إعادة ضبط", "Import / reset")) {
        OutlinedTextField(imp, { imp = it }, label = { Text(tr("الصق نص الملف (JSON)", "Paste profile JSON")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ if (vm.import(imp)) imp = "" else vm.message = tr("ملف غير صالح", "Invalid profile") }, enabled = imp.isNotBlank()) { Text(tr("استيراد", "Import")) }
            OutlinedButton({ vm.resetDefaults() }) { Text(tr("إعادة الافتراضي", "Reset defaults")) }
        }
    }
}

@Composable fun Report(vm: MainViewModel) {
    val ctx = LocalContext.current
    val t = vm.report()
    Sec(tr("تقرير التشخيص", "Diagnostic report")) {
        Text(t, style = MaterialTheme.typography.bodySmall)
        Button({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null)) }) { Text(tr("مشاركة (بقرارك فقط)", "Share (your choice only)")) }
        Text(tr("لا يُرسل شيء تلقائيًا؛ التقرير نص لا يحتوي على تسجيلات أو عناوين أجهزة.", "Nothing is sent automatically; the report is text without recordings or device addresses."), style = MaterialTheme.typography.bodySmall)
    }
}

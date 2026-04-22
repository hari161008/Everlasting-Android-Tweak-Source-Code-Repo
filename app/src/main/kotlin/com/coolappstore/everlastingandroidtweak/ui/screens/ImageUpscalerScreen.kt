package com.coolappstore.everlastingandroidtweak.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar
import com.coolappstore.everlastingandroidtweak.ui.components.InfoCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private enum class UpscalerState { IDLE, IMAGE_SELECTED, UPSCALING, DONE, ERROR }

// ─── JS ↔ Android bridge ─────────────────────────────────────────────────────
private class AutomationBridge(
    private val onBlobReady: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    @JavascriptInterface fun blobReady(base64: String) = onBlobReady(base64)
    @JavascriptInterface fun reportError(msg: String)  = onError(msg)
}

// ─── Automation JS — injects image, selects 4x, waits for result ──────────────
private fun buildAutomationJs(base64Image: String): String = """
(function() {
  try {
    var handled = false;
    function readBlob(url) {
      if (handled) return; handled = true;
      var x = new XMLHttpRequest();
      x.open('GET', url, true); x.responseType = 'arraybuffer';
      x.onload = function() {
        var arr = new Uint8Array(x.response), chunk = 8192, b = '';
        for (var i = 0; i < arr.length; i += chunk)
          b += String.fromCharCode.apply(null, arr.subarray(i, i+chunk));
        AutoBridge.blobReady(btoa(b));
      };
      x.onerror = function() { AutoBridge.reportError('XHR failed'); };
      x.send();
    }

    // Intercept blob link clicks and programmatic anchor.click()
    document.addEventListener('click', function(e) {
      var el = e.target; while (el && el.tagName !== 'A') el = el.parentElement;
      if (!el) return;
      var href = el.getAttribute('href') || el.href || '';
      if (href && href.indexOf('blob:') === 0) { e.preventDefault(); e.stopImmediatePropagation(); readBlob(href); }
    }, true);
    var _oc = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function() {
      var href = this.href || '';
      if (href.indexOf('blob:') === 0) { readBlob(href); return; } _oc.call(this);
    };
    new MutationObserver(function(ms) {
      ms.forEach(function(m) { m.addedNodes.forEach(function(n) {
        if (n.tagName === 'A' && (n.href||'').indexOf('blob:') === 0) readBlob(n.href);
      }); });
    }).observe(document.body, { childList:true, subtree:true });

    // Decode base64 -> File
    var bin = atob('$base64Image'), bytes = new Uint8Array(bin.length);
    for (var i=0;i<bin.length;i++) bytes[i]=bin.charCodeAt(i);
    var file = new File([new Blob([bytes],{type:'image/jpeg'})],'upload.jpg',{type:'image/jpeg'});

    // Inject into file input
    var inp = document.querySelector('input[type="file"]');
    if (!inp) { AutoBridge.reportError('No file input'); return; }
    var dt = new DataTransfer(); dt.items.add(file);
    inp.files = dt.files;
    inp.dispatchEvent(new Event('change',{bubbles:true}));
    inp.dispatchEvent(new Event('input',{bubbles:true}));

    // Select 4x scale
    setTimeout(function() {
      var all = Array.from(document.querySelectorAll('button,[role="button"],span,div,label,li'));
      for (var i=0;i<all.length;i++) {
        var t=(all[i].innerText||all[i].textContent||'').trim();
        if(t==='4x'||t.indexOf('400')!==-1){all[i].click();break;}
      }
      // Click upload/start
      setTimeout(function() {
        var btns = Array.from(document.querySelectorAll('button'));
        for (var j=0;j<btns.length;j++) {
          var txt=(btns[j].innerText||btns[j].textContent||'').trim().toLowerCase();
          if(txt.indexOf('upload')!==-1||txt.indexOf('start')!==-1){btns[j].click();break;}
        }
        // Poll for download
        var att=0, timer=setInterval(function(){
          if(handled){clearInterval(timer);return;}
          if(++att>300){clearInterval(timer);AutoBridge.reportError('Timeout');return;}
          var ba=Array.from(document.querySelectorAll('a[href^="blob:"]'));
          if(ba.length){clearInterval(timer);readBlob(ba[0].href);return;}
          var els=Array.from(document.querySelectorAll('button,a'));
          for(var k=0;k<els.length;k++){
            var t2=(els[k].innerText||els[k].textContent||'').trim().toLowerCase();
            if(t2==='download'||t2==='download all'||t2==='download image'||t2.startsWith('download (')){
              clearInterval(timer);els[k].click();return;
            }
          }
        },1000);
      },2000);
    },2000);
  } catch(e){ AutoBridge.reportError('JS: '+e.message); }
})();
""".trimIndent()

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun ImageUpscalerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var state             by remember { mutableStateOf(UpscalerState.IDLE) }
    var selectedUri       by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap     by remember { mutableStateOf<Bitmap?>(null) }
    var scaleChoice       by remember { mutableStateOf("2x") }
    var errorMessage      by remember { mutableStateOf("") }
    var statusText        by remember { mutableStateOf("") }
    // 8x requires two 4x passes; pass 1 stores raw intermediate bytes here
    var intermediateBytes by remember { mutableStateOf<ByteArray?>(null) }
    var currentPass       by remember { mutableStateOf(1) }

    // ── Image picker ──────────────────────────────────────────────────────────
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        selectedUri = uri
        state = UpscalerState.IMAGE_SELECTED
        intermediateBytes = null
        scope.launch(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp  = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                withContext(Dispatchers.Main) { previewBitmap = bmp }
            } catch (_: Exception) {}
        }
    }

    // ── Stable hidden WebView ─────────────────────────────────────────────────
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true
                allowFileAccess                  = true
                allowContentAccess               = true
                mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort                  = true
                loadWithOverviewMode             = true
                mediaPlaybackRequiresUserGesture = false
                setSupportMultipleWindows(false)
                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36"
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Extract raw image bytes from a ZIP, or return bytes as-is if not a ZIP. */
    fun extractFromZipOrRaw(bytes: ByteArray): ByteArray {
        if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            val zis = java.util.zip.ZipInputStream(bytes.inputStream())
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".webp")) {
                    val img = zis.readBytes(); zis.close(); return img
                }
                zis.closeEntry(); entry = zis.nextEntry
            }
            zis.close()
        }
        return bytes
    }

    /** Save bytes to Pictures/Everlasting and notify media scanner. */
    fun saveBytesToGallery(bytes: ByteArray, ext: String): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Everlasting"
        ).also { it.mkdirs() }
        val file = File(dir, "upscaled_${System.currentTimeMillis()}.$ext")
        file.writeBytes(bytes)
        // Notify media scanner so the image appears in gallery immediately
        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        return file
    }

    /** Handle the final result blob: extract from ZIP if needed, then save. */
    fun handleResultBytes(bytes: ByteArray) {
        val raw = extractFromZipOrRaw(bytes)
        val ext = when {
            raw.size > 3 && raw[0] == 0x89.toByte() -> "png"   // PNG magic
            raw.size > 3 && raw[0] == 'R'.code.toByte() -> "webp" // RIFF
            else -> "jpg"
        }
        saveBytesToGallery(raw, ext)
    }

    /** Compress image bytes to JPEG ≤ maxDim px on longest side, then base64-encode. */
    fun prepareBase64(imageBytes: ByteArray, maxDim: Int = 1200): String {
        val src = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val scaled = if (src != null && (src.width > maxDim || src.height > maxDim)) {
            val ratio = maxDim.toFloat() / maxOf(src.width, src.height)
            Bitmap.createScaledBitmap(src,
                (src.width * ratio).toInt().coerceAtLeast(1),
                (src.height * ratio).toInt().coerceAtLeast(1), true)
        } else src
        val out = ByteArrayOutputStream()
        (scaled ?: src)?.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ── Wire WebView once ─────────────────────────────────────────────────────
    DisposableEffect(webView) {
        val bridge = AutomationBridge(
            onBlobReady = { base64 ->
                if (state != UpscalerState.UPSCALING) return@AutomationBridge
                scope.launch(Dispatchers.IO) {
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        if (scaleChoice == "8x" && currentPass == 1) {
                            // First 4x pass done — prepare second pass
                            val rawImg = extractFromZipOrRaw(bytes)
                            withContext(Dispatchers.Main) {
                                currentPass = 2
                                statusText  = "Pass 2 of 2 — AI processing…"
                                intermediateBytes = rawImg
                                webView.loadUrl("https://imgupscaler.com")
                            }
                        } else {
                            // Final pass — save result
                            handleResultBytes(bytes)
                            withContext(Dispatchers.Main) {
                                state = UpscalerState.DONE
                                Toast.makeText(context,
                                    "✓ Saved to Pictures/Everlasting",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Save failed: ${e.message}"
                            state = UpscalerState.ERROR
                        }
                    }
                }
            },
            onError = { msg ->
                scope.launch(Dispatchers.Main) {
                    errorMessage = msg
                    state = UpscalerState.ERROR
                }
            }
        )

        webView.addJavascriptInterface(bridge, "AutoBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (state != UpscalerState.UPSCALING) return
                scope.launch(Dispatchers.IO) {
                    try {
                        val imageBytes = intermediateBytes ?: run {
                            val uri = selectedUri ?: return@launch
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: return@launch
                        }
                        val b64 = prepareBase64(imageBytes)
                        withContext(Dispatchers.Main) {
                            view.evaluateJavascript(buildAutomationJs(b64), null)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Prep failed: ${e.message}"
                            state = UpscalerState.ERROR
                        }
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String) = false
        }

        // Fallback for native blob download trigger
        webView.setDownloadListener { url, _, _, _, _ ->
            if (url.startsWith("blob:")) {
                val js = "(function(){var x=new XMLHttpRequest();x.open('GET','$url',true);" +
                    "x.responseType='arraybuffer';x.onload=function(){var a=new Uint8Array(x.response)," +
                    "c=8192,b='';for(var i=0;i<a.length;i+=c)b+=String.fromCharCode.apply(null,a.subarray(i,i+c));" +
                    "AutoBridge.blobReady(btoa(b));};x.onerror=function(){AutoBridge.reportError('XHR fail');};x.send();})();"
                scope.launch(Dispatchers.Main) { webView.evaluateJavascript(js, null) }
            }
        }

        onDispose { webView.stopLoading(); webView.destroy() }
    }

    // ── Kick off upscaling when state changes to UPSCALING ───────────────────
    LaunchedEffect(state) {
        if (state == UpscalerState.UPSCALING) {
            currentPass       = 1
            intermediateBytes = null
            statusText = if (scaleChoice == "8x") "Pass 1 of 2 — AI processing…" else "AI processing…"
            webView.loadUrl("https://imgupscaler.com")
        }
    }

    // ── Pulsing border animation for drop zone ────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "anim")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "border"
    )

    // ── Hidden 1dp WebView keeps JS alive without showing anything ────────────
    Box(Modifier.size(1.dp)) {
        AndroidView(factory = { webView }, modifier = Modifier.size(1.dp))
    }

    Scaffold(
        topBar = { EverlastingTopBar(title = "AI Image Upscaler", navController = navController) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(Modifier.size(52.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoFixHigh, null, Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("AI Image Upscaler",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Enhance photos 2×, 4×, or 8× using AI super-resolution.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    }
                }
            }

            // ── Processing indicator ──────────────────────────────────────────
            AnimatedVisibility(
                visible = state == UpscalerState.UPSCALING,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(72.dp),
                                color = MaterialTheme.colorScheme.secondary, strokeWidth = 5.dp)
                            Icon(Icons.Default.AutoFixHigh, null, Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.secondary)
                        }
                        Text("AI Processing…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center)
                        if (scaleChoice == "8x") {
                            LinearProgressIndicator(
                                progress = { if (currentPass == 1) 0.5f else 1f },
                                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                "Pass $currentPass of 2",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.6f)
                            )
                        }
                    }
                }
            }

            // ── Success banner ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = state == UpscalerState.DONE,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.tertiary)
                        Column(Modifier.weight(1f)) {
                            Text("Image Saved!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Upscaled ${scaleChoice} · Saved to Pictures/Everlasting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // ── Error banner ──────────────────────────────────────────────────
            AnimatedVisibility(visible = state == UpscalerState.ERROR) {
                InfoCard(icon = Icons.Default.Error,
                    title = "Something Went Wrong",
                    subtitle = errorMessage.ifEmpty { "The website may have changed. Try again." },
                    isError = true, actionLabel = "Retry",
                    onAction = {
                        state = if (selectedUri != null) UpscalerState.IMAGE_SELECTED
                                else UpscalerState.IDLE
                        errorMessage = ""
                        intermediateBytes = null
                    })
            }

            // ── Image picker zone ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = state != UpscalerState.UPSCALING,
                enter = fadeIn(), exit = fadeOut()
            ) {
                val hasImage = previewBitmap != null && state != UpscalerState.IDLE
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { imagePicker.launch("image/*") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasImage) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = if (!hasImage) BorderStroke(
                        2.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha)
                    ) else null,
                    elevation = CardDefaults.cardElevation(defaultElevation = if (hasImage) 0.dp else 2.dp)
                ) {
                    if (hasImage && previewBitmap != null) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 300.dp)) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Selected image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(min = 200.dp, max = 300.dp)
                                    .clip(MaterialTheme.shapes.extraLarge)
                            )
                            // Change button
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        .clickable { imagePicker.launch("image/*") },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, null, Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Text("Change", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(Modifier.size(80.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            Text("Tap to Choose an Image",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text("JPG, PNG or WebP — any resolution",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // ── Scale picker ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible = state == UpscalerState.IMAGE_SELECTED || state == UpscalerState.DONE,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Upscale Factor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 10.dp, start = 2.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("2x", "2×", "Good"),
                            Triple("4x", "4×", "Great"),
                            Triple("8x", "8×", "Max · 2 passes")
                        ).forEach { (key, label, sub) ->
                            val selected = scaleChoice == key
                            Card(
                                modifier = Modifier.weight(1f).clickable { scaleChoice = key },
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = if (selected)
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (selected) 0.dp else 1.dp)
                            ) {
                                Column(
                                    Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(label,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        color = if (selected) MaterialTheme.colorScheme.primary.copy(0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Upscale button ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = state == UpscalerState.IMAGE_SELECTED || state == UpscalerState.DONE,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()
            ) {
                Button(
                    onClick = { state = UpscalerState.UPSCALING },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp).height(56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (state == UpscalerState.DONE) "Upscale Another Image"
                        else "Upscale with AI ($scaleChoice)",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            // ── How it works ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible = state == UpscalerState.IDLE || state == UpscalerState.IMAGE_SELECTED,
                enter = fadeIn(), exit = fadeOut()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("How It Works",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 10.dp, start = 2.dp))
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            listOf(
                                Triple(Icons.Default.AddPhotoAlternate, "Pick Your Image",
                                    "Select any JPEG, PNG, or WebP photo from your gallery."),
                                Triple(Icons.Default.Tune, "Choose Scale",
                                    "2× good · 4× great · 8× maximum (runs two AI passes in sequence)."),
                                Triple(Icons.Default.AutoFixHigh, "Fully Automatic",
                                    "Uploads, processes, extracts from ZIP, downloads — all in the background."),
                                Triple(Icons.Default.DownloadDone, "Auto-Saved",
                                    "Result saved directly to Pictures/Everlasting and shown in your gallery.")
                            ).forEachIndexed { i, (icon, title, desc) ->
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.Top) {
                                    Box(Modifier.size(38.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center) {
                                        Icon(icon, null, Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(title, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(2.dp))
                                        Text(desc, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (i < 3) HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state == UpscalerState.IDLE || state == UpscalerState.IMAGE_SELECTED,
                enter = fadeIn(), exit = fadeOut()
            ) {
                InfoCard(
                    icon = Icons.Default.Language,
                    title = "Powered by imgupscaler.com",
                    subtitle = "Runs entirely in the background — you never leave the app. 4x results delivered as ZIP are automatically extracted.",
                    isError = false
                )
            }
        }
    }
}

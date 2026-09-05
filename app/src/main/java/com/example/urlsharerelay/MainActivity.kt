package com.example.urlsharerelay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var incomingUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUrl.value = extractUrl(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RelayScreen(
                        initialUrl = incomingUrl.value,
                        onShare = { title, description, url ->
                            shareResult(title, description, url)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl.value = extractUrl(intent)
    }

    private fun extractUrl(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return null

        val url = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
            .find(text)?.value
            ?.trimEnd('.', ',', ';', ')', ']', '}')

        return url
    }

    private fun shareResult(title: String, description: String, url: String) {
        val body = buildString {
            if (title.isNotBlank()) append(title.trim())
            if (description.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(description.trim())
            }
            if (isNotBlank()) append("\n\n")
            append(url.trim())
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_TEXT, body)
            clipData = android.content.ClipData.newPlainText("URL", body)
        }

        startActivity(Intent.createChooser(send, "Share link"))
    }
}

data class PageMetadata(
    val title: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val canonicalUrl: String? = null
)

@androidx.compose.runtime.Composable
private fun RelayScreen(
    initialUrl: String?,
    onShare: (String, String, String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl ?: "") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadMetadata() {
        val normalized = normalizeUrl(url)
        if (normalized == null) {
            error = "Enter a valid http:// or https:// URL."
            return
        }

        url = normalized
        loading = true
        error = null

        try {
            val metadata = withContext(Dispatchers.IO) {
                MetadataFetcher.fetch(normalized)
            }
            title = metadata.title
            description = metadata.description
            imageUrl = metadata.imageUrl
            metadata.canonicalUrl?.let { url = it }
        } catch (e: Exception) {
            error = e.message ?: "Unable to load page metadata."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            loadMetadata()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("URL Share Relay", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Receive a link from another app, fetch its metadata, then share it again.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        val scope = androidx.compose.runtime.rememberCoroutineScope()

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = !loading && url.isNotBlank(),
                onClick = { scope.launch { loadMetadata() } }
            ) {
                Text("Fetch metadata")
            }
            Button(
                enabled = !loading && url.isNotBlank(),
                onClick = {
                    val normalized = normalizeUrl(url)
                    if (normalized != null) onShare(title, description, normalized)
                    else error = "Enter a valid URL."
                }
            ) {
                Text("Share again")
            }
        }

        if (loading) {
            CircularProgressIndicator()
            Text("Fetching page metadata…")
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (title.isNotBlank()) {
            Text("Preview", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            imageUrl?.let {
                Text("Image: $it", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Text(url, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun normalizeUrl(value: String): String? {
    val candidate = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        .find(value.trim())?.value
        ?.trimEnd('.', ',', ';', ')', ']', '}')
        ?: return null

    return try {
        val uri = Uri.parse(candidate)
        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            if (!uri.host.isNullOrBlank()) candidate else null
        } else null
    } catch (_: Exception) {
        null
    }
}

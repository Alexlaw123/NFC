package com.example.nfctool

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcEvent
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.nfctool.ui.theme.NFCToolTheme
import android.util.Log
import android.content.IntentFilter
import android.nfc.tech.NfcA
import android.nfc.tech.MifareClassic
import java.nio.charset.Charset
import android.nfc.tech.MifareUltralight
import android.widget.TextView


class MainActivity : ComponentActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFiltersArray: Array<IntentFilter>
    private lateinit var techListsArray: Array<Array<String>>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Log.e("NFC", "NFC is not supported on this device.")
            return
        }

        Log.d("NFC", "NFC adapter initialized")

        // PendingIntent to capture the NFC intent and redirect to this activity
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Define intent filters for NDEF_DISCOVERED and TECH_DISCOVERED
        val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("text/plain")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                Log.e("NFC", "Malformed MIME type", e)
            }
        }

        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)

        // Array of filters for dispatch
        intentFiltersArray = arrayOf(ndefFilter, techFilter)

        // Array of tech-list for detecting non-NDEF tags like MifareClassic
        techListsArray = arrayOf(
            arrayOf(MifareClassic::class.java.name),
            arrayOf(Ndef::class.java.name)
        )

        Log.d("NFC", "NFC intent filters and tech lists set up.")
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
            Log.d("NFC", "Foreground dispatch enabled")
        }
    }

    override fun onPause() {
        super.onPause()
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this)
            Log.d("NFC", "Foreground dispatch disabled")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("NFC", "onNewIntent triggered with intent: $intent")
        intent?.let {
            val action = it.action
            Log.d("NFC", "LZD Intent action: $action")

            // Check for NDEF tag
            if (NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
                Log.d("NFC", "NDEF tag detected")
                val tag = it.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                tag?.let { t -> processNdefTag(t) }
            }

            // Check for tech-based tag (e.g., MifareClassic)
            else if (NfcAdapter.ACTION_TECH_DISCOVERED == action) {
                Log.d("NFC", "Tech-based tag detected")
                val tag = it.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)

                tag?.let { t -> processTechTag(t) }
            } else {
                Log.d("NFC", "Unknown NFC action detected")
            }
        } ?: run {
            Log.e("NFC", "Received null intent in onNewIntent.")
        }
    }

    private fun processNdefTag(tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            Log.d("NFC", "NDEF tag found")
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            ndef.close()

            // Process each NDEF record and display it
            ndefMessage?.let {
                displayNdefMessage(it)
            }
        } else {
            Log.d("NFC", "NDEF not supported on this tag")
        }
    }

    private fun displayNdefMessage(ndefMessage: NdefMessage) {
        val textView = findViewById<TextView>(R.id.textViewNdefContent)
        val messageBuilder = StringBuilder()

        for (record in ndefMessage.records) {
            when (record.tnf) {
                NdefRecord.TNF_WELL_KNOWN -> {
                    when {
                        record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                            val text = String(record.payload, Charset.forName("UTF-8"))
                            messageBuilder.append("Text Record: $text\n")
                        }
                        record.type.contentEquals(NdefRecord.RTD_URI) -> {
                            val uri = record.toUri()
                            messageBuilder.append("URI Record: $uri\n")
                        }
                    }
                }
            }
        }

        textView.text = messageBuilder.toString()
    }


    private fun processTechTag(tag: Tag) {
        Log.d("NFC", "Tech-based tag detected: ${tag.techList}")

        // Check if the tag is MifareClassic
        val mifareClassic = MifareClassic.get(tag)
        if (mifareClassic != null) {
            Log.d("NFC", "MifareClassic tag found")
            try {
                mifareClassic.connect()
                val sectorCount = mifareClassic.sectorCount
                Log.d("NFC", "MifareClassic tag sector count: $sectorCount")
                val uid = tag.id.toHexString()
                val nfca = NfcA.get(tag)  // Retrieve ATQA and SAK through NfcA
                val atqa = nfca?.atqa?.toHexString()
                val sak = nfca?.sak
                Log.d("NFC", "MifareClassic UID: $uid")
                Log.d("NFC", "MifareClassic ATQA: $atqa")
                Log.d("NFC", "MifareClassic SAK: $sak")
                Log.d("NFC", "MifareClassic sector count: $sectorCount")
                // Add more MifareClassic processing here
            } catch (e: Exception) {
                Log.e("NFC", "Error reading MifareClassic tag", e)
            } finally {
                try {
                    mifareClassic.close()
                } catch (e: Exception) {
                    Log.e("NFC", "Error closing MifareClassic tag", e)
                }
            }
            return
        }

        // Check if the tag is MifareUltralight
        val mifareUltralight = MifareUltralight.get(tag)
        if (mifareUltralight != null) {
            Log.d("NFC", "MifareUltralight tag found")
            try {
                mifareUltralight.connect()
                val payload = mifareUltralight.readPages(0) // Example to read from page 0
                Log.d("NFC", "MifareUltralight payload: ${String(payload, Charsets.UTF_8)}")
                Log.d("NFC", "MifareUltralight UID: ${tag.id.toHexString()}")
                // 获取TextView
                    val textViewUid = findViewById<TextView>(R.id.textViewUid)
                // 设置TextView的文本
                textViewUid.text = "MifareUltralight UID: ${tag.id.toHexString()}"
            } catch (e: Exception) {
                Log.e("NFC", "Error reading MifareUltralight tag", e)
            } finally {
                try {
                    mifareUltralight.close()
                } catch (e: Exception) {
                    Log.e("NFC", "Error closing MifareUltralight tag", e)
                }
            }
            return
        }

        // Check if the tag is NfcA
        val nfcA = NfcA.get(tag)
        if (nfcA != null) {
            Log.d("NFC", "NfcA tag found")
            try {
                nfcA.connect()
                val response = nfcA.transceive(byteArrayOf(0x00)) // Replace with a valid command for NfcA
                Log.d("NFC", "NfcA response: ${response.toHexString()}") // Implement toHexString extension to convert byte array to hex
                Log.d("NFC", "NfcA UID: ${tag.id.toHexString()}")
            } catch (e: Exception) {
                Log.e("NFC", "Error reading NfcA tag", e)
            } finally {
                try {
                    nfcA.close()
                } catch (e: Exception) {
                    Log.e("NFC", "Error closing NfcA tag", e)
                }
            }
            return
        }



        Log.e("NFC", "No compatible tag found.")
    }

    // Extension function to convert byte array to hex string
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

}

@Composable
fun NFCButtons(
    onReadClick: () -> Unit,
    onWriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Button(onClick = onReadClick) {
            Text(text = "Read NFC")
        }
        Button(onClick = onWriteClick) {
            Text(text = "Write NFC")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NFCButtonsPreview() {
    NFCToolTheme {
        NFCButtons(onReadClick = {}, onWriteClick = {})
    }
}

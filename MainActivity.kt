package com.footballaipro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Navy = Color(0xFF0B1220)
private val Panel = Color(0xFF172235)
private val Green = Color(0xFF27D69B)
private val Muted = Color(0xFF9AA9BD)

data class MatchItem(
    val id: Int, val home: String, val away: String,
    val league: String, val kickoff: String, val status: String
)

data class PredictionInfo(
    val advice: String, val home: String, val draw: String, val away: String,
    val underOver: String, val goalsHome: String, val goalsAway: String
)

data class CardInfo(val minute: String, val team: String, val detail: String)

class ApiFootball(private val apiKey: String) {
    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("https://v3.football.api-sports.io/$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("x-apisports-key", apiKey)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (code !in 200..299) {
                val apiErrors = json.optJSONObject("errors")?.toString() ?: body.take(250)
                throw IllegalStateException("API error HTTP $code: $apiErrors")
            }
            val errors = json.optJSONObject("errors")
            if (errors != null && errors.length() > 0) {
                throw IllegalStateException("API error: $errors")
            }
            json
        } finally { connection.disconnect() }
    }

    suspend fun fixtures(date: String): List<MatchItem> {
        val json = get("fixtures?date=$date&timezone=Asia%2FDhaka")
        val arr = json.optJSONArray("response") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            val fixture = item.optJSONObject("fixture") ?: return@mapNotNull null
            val teams = item.optJSONObject("teams") ?: return@mapNotNull null
            val league = item.optJSONObject("league")
            val home = teams.optJSONObject("home")?.optString("name").orEmpty()
            val away = teams.optJSONObject("away")?.optString("name").orEmpty()
            MatchItem(
                fixture.optInt("id"), home, away,
                league?.optString("name").orEmpty(),
                fixture.optString("date"),
                fixture.optJSONObject("status")?.optString("short").orEmpty()
            )
        }
    }

    suspend fun prediction(fixtureId: Int): PredictionInfo? {
        val arr = get("predictions?fixture=$fixtureId").optJSONArray("response") ?: return null
        if (arr.length() == 0) return null
        val predictions = arr.getJSONObject(0).optJSONObject("predictions") ?: return null
        val percent = predictions.optJSONObject("percent")
        val goals = predictions.optJSONObject("goals")
        return PredictionInfo(
            predictions.optString("advice", "No advice available"),
            percent?.optString("home", "N/A") ?: "N/A",
            percent?.optString("draw", "N/A") ?: "N/A",
            percent?.optString("away", "N/A") ?: "N/A",
            predictions.optString("under_over", "N/A"),
            goals?.optString("home", "N/A") ?: "N/A",
            goals?.optString("away", "N/A") ?: "N/A"
        )
    }

    suspend fun earlyCards(fixtureId: Int): List<CardInfo> {
        val arr = get("fixtures/events?fixture=$fixtureId").optJSONArray("response") ?: return emptyList()
        val found = mutableListOf<CardInfo>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (e.optString("type") != "Card") continue
            val time = e.optJSONObject("time")
            val elapsed = time?.optInt("elapsed", 999) ?: 999
            val extra = time?.optInt("extra", 0) ?: 0
            if (elapsed <= 5 && elapsed >= 0) {
                val team = e.optJSONObject("team")?.optString("name").orEmpty()
                val detail = e.optString("detail", "Card")
                found.add(CardInfo(if (extra > 0) "$elapsed+$extra′" else "$elapsed′", team, detail))
            }
        }
        return found
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("football_ai_pro", MODE_PRIVATE)
        setContent {
            var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
            var keyInput by remember { mutableStateOf(apiKey) }
            var date by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Dhaka")).format(DateTimeFormatter.ISO_DATE)) }
            var matches by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
            var selected by remember { mutableStateOf<MatchItem?>(null) }
            var prediction by remember { mutableStateOf<PredictionInfo?>(null) }
            var earlyCards by remember { mutableStateOf<List<CardInfo>>(emptyList()) }
            var loading by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf("API key যোগ করে ম্যাচ লোড করুন।") }
            var tab by remember { mutableStateOf("matches") }
            val scope = rememberCoroutineScope()

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Green, background = Navy, surface = Panel,
                onBackground = Color.White, onSurface = Color.White
            )) {
                Column(Modifier.fillMaxSize().background(Navy).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("FOOTBALL AI PRO", color = Green, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text("Football statistics & probability lab", color = Muted, fontSize = 12.sp)
                        }
                        Text("FREE API", color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabButton("Matches", tab == "matches") { tab = "matches"; selected = null }
                        TabButton("Settings", tab == "settings") { tab = "settings" }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (tab == "settings") {
                        Text("API configuration", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("API-Football (API-SPORTS) key", color = Muted)
                        OutlinedTextField(
                            value = keyInput, onValueChange = { keyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste your API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            apiKey = keyInput.trim()
                            prefs.edit().putString("api_key", apiKey).apply()
                            message = if (apiKey.isNotBlank()) "API key saved on this device." else "API key cleared."
                        }) { Text("Save API Key") }
                        Spacer(Modifier.height(10.dp))
                        Text("Free plan quota: 100 requests/day, subject to provider limits. API key এই ফোনে সংরক্ষিত থাকবে।", color = Muted, fontSize = 12.sp)
                        Text(message, color = Green)
                    } else if (selected == null) {
                        Text("Match Scanner", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = date, onValueChange = { date = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Date (YYYY-MM-DD)") }, singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !loading && apiKey.isNotBlank(),
                            onClick = {
                                loading = true; message = "ম্যাচ খোঁজা হচ্ছে..."
                                scope.launch {
                                    try {
                                        matches = ApiFootball(apiKey).fixtures(date.trim())
                                        message = "${matches.size} টি ম্যাচ পাওয়া গেছে।"
                                    } catch (e: Exception) { message = e.message ?: "Unknown error" }
                                    loading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (loading) "Loading..." else "Load Matches") }
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(matches) { match ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selected = match; prediction = null; earlyCards = emptyList()
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Panel),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(match.league, color = Green, fontSize = 11.sp)
                                        Text("${match.home}  vs  ${match.away}", fontWeight = FontWeight.Bold)
                                        Text("${match.kickoff.replace("T", " ")}  •  ${match.status}", color = Muted, fontSize = 11.sp)
                                        Text("Tap for analysis →", color = Green, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        val match = selected!!
                        Text("Match Analysis", color = Green, fontWeight = FontWeight.Bold)
                        Text("${match.home} vs ${match.away}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${match.league} • ${match.kickoff.replace("T", " ")}", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            enabled = !loading && apiKey.isNotBlank(),
                            onClick = {
                                loading = true; message = "Prediction data loading..."
                                scope.launch {
                                    try {
                                        prediction = ApiFootball(apiKey).prediction(match.id)
                                        message = if (prediction == null) "এই ম্যাচের prediction data পাওয়া যায়নি।" else "Provider prediction loaded."
                                    } catch (e: Exception) { message = e.message ?: "Error" }
                                    loading = false
                                }
                            }, modifier = Modifier.fillMaxWidth()
                        ) { Text("Load 1X2 / Goals Analysis") }
                        prediction?.let { p ->
                            InfoCard("Provider prediction", p.advice)
                            InfoCard("1X2 probability", "Home ${p.home}  •  Draw ${p.draw}  •  Away ${p.away}")
                            InfoCard("Goals estimate", "Home ${p.goalsHome}  •  Away ${p.goalsAway}  •  Under/Over ${p.underOver}")
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !loading && apiKey.isNotBlank(),
                            onClick = {
                                loading = true; message = "Card events checking..."
                                scope.launch {
                                    try {
                                        earlyCards = ApiFootball(apiKey).earlyCards(match.id)
                                        message = if (earlyCards.isEmpty()) "প্রথম ৫ মিনিটে কোনো Card event পাওয়া যায়নি (ডেটা অনুপস্থিতিও হতে পারে)।" else "প্রথম ৫ মিনিটে ${earlyCards.size} টি card event পাওয়া গেছে।"
                                    } catch (e: Exception) { message = e.message ?: "Error" }
                                    loading = false
                                }
                            }, modifier = Modifier.fillMaxWidth()
                        ) { Text("Check First 5-Minute Cards") }
                        Text(message, color = Muted, fontSize = 12.sp)
                        earlyCards.forEach { c -> InfoCard("${c.minute} • ${c.detail}", c.team) }
                        Spacer(Modifier.height(8.dp))
                        InfoCard("BTTS / Double Chance / O-U", "শুধু নির্ভরযোগ্য ম্যাচ-নির্দিষ্ট ডেটা পাওয়া গেলে পরবর্তী সংস্করণে দেখানো হবে।")
                        TextButton(onClick = { selected = null }) { Text("← Back to matches") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("বিশ্লেষণ তথ্যভিত্তিক; নিশ্চিত জয়ের নিশ্চয়তা নয়। API ডেটা অসম্পূর্ণ হলে ফলাফলও অসম্পূর্ণ হতে পারে।", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(
        containerColor = if (selected) Green else Panel,
        contentColor = if (selected) Navy else Color.White
    )) { Text(label) }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(body, color = Color.White, fontSize = 14.sp)
        }
    }
}

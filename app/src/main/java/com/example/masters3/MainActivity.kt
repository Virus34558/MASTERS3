package com.example.masters3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.masters3.ui.theme.MASTERS3Theme
import kotlin.random.Random

// --- Data Classes ---
data class Jugador(
    val nombre: String,
    val posicion: String,
    var goles: Int = 0,
    var asistencias: Int = 0,
    var paradas: Int = 0,
    val isCapitan: Boolean = false,
    val isPortero: Boolean = false
)

data class Equipo(
    val nombre: String,
    val jugadores: List<Jugador>,
    val puntos: Int = 0,
    val golesAFavor: Int = 0,
    val golesEnContra: Int = 0,
    val ganados: Int = 0,
    val empatados: Int = 0,
    val perdidos: Int = 0
)

data class Partido(
    val local: Equipo,
    val visitante: Equipo,
    val golesLocal: Int? = null,
    val golesVisitante: Int? = null,
    val isPlayoffMatch: Boolean = false
)

data class TopPlayerStat(val nombre: String, val equipo: String, val value: Int, val isCapitan: Boolean)
data class SeasonResult(val seasonNumber: Int, val champion: String, val runnerUp: String, val topScorer: TopPlayerStat, val topAssister: TopPlayerStat, val topKeeper: TopPlayerStat)
data class PlayerInputState(var name: String = "", var position: String = "")

// --- Simulated Persistent Team Repository ---
object TeamRepository {
    val savedTeams = mutableStateListOf<Equipo>()
    fun addTeam(equipo: Equipo) { if (savedTeams.none { it.nombre == equipo.nombre }) savedTeams.add(equipo) }
    fun deleteTeam(teamName: String) { savedTeams.removeAll { it.nombre == teamName } }
}

// --- Navigation & State ---
sealed class Screen {
    object MainMenu : Screen()
    object AddTeams : Screen()
    object ViewCalendar : Screen()
    object ViewClassification : Screen()
    object ViewStats : Screen()
    object History : Screen()
}
enum class GamePhase { REGULAR_SEASON, PLAYOFFS, SEASON_END }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MASTERS3Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LeagueManagerApp(onExit = { finish() })
                }
            }
        }
    }
}

@Composable
fun LeagueManagerApp(onExit: () -> Unit) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.MainMenu) }
    val equiposEnLiga = remember { mutableStateListOf<Equipo>() }
    val partidos = remember { mutableStateListOf<Partido>() }
    val playoffMatches = remember { mutableStateListOf<Partido>() }
    val seasonHistory = remember { mutableStateListOf<SeasonResult>() }

    var nextMatchIndex by remember { mutableIntStateOf(0) }
    var nextPlayoffMatchIndex by remember { mutableIntStateOf(0) }
    var lastMatchResult by remember { mutableStateOf<String?>(null) }
    var gamePhase by remember { mutableStateOf(GamePhase.REGULAR_SEASON) }
    var seasonNumber by remember { mutableIntStateOf(1) }

    val onNavigate: (Screen) -> Unit = { screen -> lastMatchResult = null; currentScreen = screen }

    val startNewSeason = {
        if (seasonHistory.size >= 5) seasonHistory.clear()
        val resetEquipos = equiposEnLiga.map { team -> team.copy(puntos = 0, golesAFavor = 0, golesEnContra = 0, ganados = 0, empatados = 0, perdidos = 0, jugadores = team.jugadores.map { it.copy(goles = 0, asistencias = 0, paradas = 0) }) }
        equiposEnLiga.clear(); equiposEnLiga.addAll(resetEquipos)
        partidos.clear(); partidos.addAll(generarCalendario(equiposEnLiga))
        playoffMatches.clear()
        nextMatchIndex = 0; nextPlayoffMatchIndex = 0; lastMatchResult = null; seasonNumber++; gamePhase = GamePhase.REGULAR_SEASON
    }

    val onSimulateMatch: () -> Unit = {
        val positionWeights = mapOf("DL" to 5, "MCO" to 4, "MC" to 3, "MCD" to 2, "DF" to 1)

        if (gamePhase == GamePhase.REGULAR_SEASON) {
            partidos.getOrNull(nextMatchIndex)?.let { partido ->
                val currentLocal = equiposEnLiga.find { it.nombre == partido.local.nombre }!!
                val currentVisitante = equiposEnLiga.find { it.nombre == partido.visitante.nombre }!!

                val golesLocal = Random.nextInt(0, 6)
                val golesVisitante = Random.nextInt(0, 6)

                val outfieldPlayersLocal = currentLocal.jugadores.filter { !it.isPortero }
                val outfieldPlayersVisitante = currentVisitante.jugadores.filter { !it.isPortero }

                // Simulate Goals, Assists, and Saves
                outfieldPlayersLocal.forEach { it.paradas = 0 } // Reset saves for the match
                outfieldPlayersVisitante.forEach { it.paradas = 0 }

                repeat(golesLocal) {
                    val weightedPlayers = outfieldPlayersLocal.flatMap { player -> List(positionWeights[player.posicion] ?: 1) { player } }
                    if (weightedPlayers.isNotEmpty()) {
                        val goleador = weightedPlayers.random()
                        goleador.goles++
                        outfieldPlayersLocal.filter { it != goleador }.randomOrNull()?.asistencias++
                    }
                }
                currentVisitante.jugadores.find { it.isPortero }?.let { it.paradas += Random.nextInt(1, 7) }

                repeat(golesVisitante) {
                    val weightedPlayers = outfieldPlayersVisitante.flatMap { player -> List(positionWeights[player.posicion] ?: 1) { player } }
                    if (weightedPlayers.isNotEmpty()) {
                        val goleador = weightedPlayers.random()
                        goleador.goles++
                        outfieldPlayersVisitante.filter { it != goleador }.randomOrNull()?.asistencias++
                    }
                }
                currentLocal.jugadores.find { it.isPortero }?.let { it.paradas += Random.nextInt(1, 7) }

                partidos[nextMatchIndex] = partido.copy(golesLocal = golesLocal, golesVisitante = golesVisitante)
                val (localActualizado, visitanteActualizado) = actualizarEstadisticas(currentLocal, currentVisitante, golesLocal, golesVisitante)
                equiposEnLiga[equiposEnLiga.indexOfFirst { it.nombre == localActualizado.nombre }] = localActualizado
                equiposEnLiga[equiposEnLiga.indexOfFirst { it.nombre == visitanteActualizado.nombre }] = visitanteActualizado

                nextMatchIndex++
                lastMatchResult = "${partido.local.nombre} $golesLocal - $golesVisitante ${partido.visitante.nombre}"

                if (nextMatchIndex >= partidos.size) {
                    gamePhase = GamePhase.PLAYOFFS
                    val top4 = equiposEnLiga.sortedWith(compareByDescending<Equipo> { it.puntos }.thenByDescending { it.golesAFavor - it.golesEnContra }).take(4)
                    if (top4.size == 4) {
                        playoffMatches.add(Partido(top4[0], top4[3], isPlayoffMatch = true))
                        playoffMatches.add(Partido(top4[1], top4[2], isPlayoffMatch = true))
                    }
                }
            }
        } else if (gamePhase == GamePhase.PLAYOFFS) {
            playoffMatches.getOrNull(nextPlayoffMatchIndex)?.let { partido ->
                var golesLocal: Int; var golesVisitante: Int
                do { golesLocal = Random.nextInt(0, 6); golesVisitante = Random.nextInt(0, 6) } while (golesLocal == golesVisitante)

                val winner = if (golesLocal > golesVisitante) partido.local else partido.visitante
                playoffMatches[nextPlayoffMatchIndex] = partido.copy(golesLocal = golesLocal, golesVisitante = golesVisitante)
                lastMatchResult = "(P) ${partido.local.nombre} $golesLocal - $golesVisitante ${partido.visitante.nombre} (Gana: ${winner.nombre})"
                nextPlayoffMatchIndex++

                if (nextPlayoffMatchIndex == 2 && playoffMatches.size == 2) {
                    val semiFinal1 = playoffMatches[0]
                    val semiFinal2 = playoffMatches[1]
                    val winner1 = if (semiFinal1.golesLocal!! > semiFinal1.golesVisitante!!) semiFinal1.local else semiFinal1.visitante
                    val winner2 = if (semiFinal2.golesLocal!! > semiFinal2.golesVisitante!!) semiFinal2.local else semiFinal2.visitante
                    playoffMatches.add(Partido(winner1, winner2, isPlayoffMatch = true))
                }

                if (nextPlayoffMatchIndex == 3) {
                    gamePhase = GamePhase.SEASON_END
                    val finalMatch = playoffMatches[2]
                    val champion = if (finalMatch.golesLocal!! > finalMatch.golesVisitante!!) finalMatch.local else finalMatch.visitante
                    val runnerUp = if (finalMatch.golesLocal > finalMatch.golesVisitante) finalMatch.visitante else finalMatch.local

                    val topScorer = equiposEnLiga.flatMap { it.jugadores }.maxByOrNull { it.goles }?.let { p -> TopPlayerStat(p.nombre, equiposEnLiga.find { it.jugadores.contains(p) }!!.nombre, p.goles, p.isCapitan) } ?: TopPlayerStat("N/A", "", 0, false)
                    val topAssister = equiposEnLiga.flatMap { it.jugadores }.maxByOrNull { it.asistencias }?.let { p -> TopPlayerStat(p.nombre, equiposEnLiga.find { it.jugadores.contains(p) }!!.nombre, p.asistencias, p.isCapitan) } ?: TopPlayerStat("N/A", "", 0, false)
                    val topKeeper = equiposEnLiga.flatMap { it.jugadores }.filter { it.isPortero }.maxByOrNull { it.paradas }?.let { p -> TopPlayerStat(p.nombre, equiposEnLiga.find { it.jugadores.contains(p) }!!.nombre, p.paradas, p.isCapitan) } ?: TopPlayerStat("N/A", "", 0, false)

                    seasonHistory.add(SeasonResult(seasonNumber, champion.nombre, runnerUp.nombre, topScorer, topAssister, topKeeper))
                }
            }
        }
    }

    when (currentScreen) {
        is Screen.MainMenu -> MainMenuScreen(onNavigate, onSimulateMatch, onExit, startNewSeason, equiposEnLiga.isNotEmpty(), gamePhase, lastMatchResult, playoffMatches.isNotEmpty())
        is Screen.AddTeams -> AddTeamsScreen(
            currentLeagueTeams = equiposEnLiga,
            savedTeams = TeamRepository.savedTeams,
            onAddToLeague = { equipo -> if (equiposEnLiga.none { it.nombre == equipo.nombre }) equiposEnLiga.add(equipo) },
            onSaveNewTeam = { equipo -> TeamRepository.addTeam(equipo) },
            onDeleteSavedTeam = { teamName -> TeamRepository.deleteTeam(teamName); equiposEnLiga.removeAll { it.nombre == teamName } },
            onDone = { partidos.clear(); partidos.addAll(generarCalendario(equiposEnLiga)); nextMatchIndex = 0; onNavigate(Screen.MainMenu) }
        )
        is Screen.ViewCalendar -> CalendarScreen(partidos, playoffMatches, onBack = { onNavigate(Screen.MainMenu) })
        is Screen.ViewClassification -> ClassificationScreen(equiposEnLiga, onBack = { onNavigate(Screen.MainMenu) })
        is Screen.ViewStats -> StatsScreen(equiposEnLiga, partidos, onBack = { onNavigate(Screen.MainMenu) })
        is Screen.History -> HistoryScreen(seasonHistory, onBack = { onNavigate(Screen.MainMenu) })
    }
}

@Composable
fun MainMenuScreen(onNavigate: (Screen) -> Unit, onSimulateMatch: () -> Unit, onExit: () -> Unit, onStartNewSeason: () -> Unit, hasTeams: Boolean, gamePhase: GamePhase, lastMatchResult: String?, hasPlayoffMatches: Boolean) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Menú Principal", style = MaterialTheme.typography.headlineMedium)
        if (lastMatchResult != null) { Spacer(Modifier.height(24.dp)); Text("Último Resultado", style = MaterialTheme.typography.titleMedium); Text(text = lastMatchResult, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)) } else { Spacer(Modifier.height(32.dp)) }
        Spacer(Modifier.height(16.dp))
        if (gamePhase == GamePhase.SEASON_END) {
            Button(onClick = onStartNewSeason, modifier = Modifier.fillMaxWidth()) { Text("Empezar Nueva Temporada") }
        } else {
            val hasMatches = gamePhase == GamePhase.REGULAR_SEASON || (gamePhase == GamePhase.PLAYOFFS && hasPlayoffMatches)
            val buttonText = if (gamePhase == GamePhase.PLAYOFFS) "Jugar Playoffs" else "Jugar Jornada"
            Button(onClick = { onNavigate(Screen.AddTeams) }, modifier = Modifier.fillMaxWidth()) { Text("Administrar Equipos") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate(Screen.ViewCalendar) }, enabled = hasTeams, modifier = Modifier.fillMaxWidth()) { Text("Ver Calendario") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSimulateMatch, enabled = hasMatches, modifier = Modifier.fillMaxWidth()) { Text(buttonText) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate(Screen.ViewClassification) }, enabled = hasTeams, modifier = Modifier.fillMaxWidth()) { Text("Ver Clasificación") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate(Screen.ViewStats) }, enabled = hasTeams, modifier = Modifier.fillMaxWidth()) { Text("Ver Estadísticas") }
        }
        Spacer(Modifier.height(8.dp)); Button(onClick = { onNavigate(Screen.History) }, modifier = Modifier.fillMaxWidth()) { Text("Ver Historial") }; Spacer(Modifier.height(16.dp)); Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Salir") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTeamsScreen(
    currentLeagueTeams: List<Equipo>,
    savedTeams: List<Equipo>,
    onAddToLeague: (Equipo) -> Unit,
    onSaveNewTeam: (Equipo) -> Unit,
    onDeleteSavedTeam: (String) -> Unit,
    onDone: () -> Unit
) {
    var teamName by remember { mutableStateOf("") }
    var playersInfo by remember { mutableStateOf(List(5) { PlayerInputState() }) }
    var captainIndex by remember { mutableIntStateOf(-1) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // --- Saved Teams Section ---
        item {
            Text("Mis Equipos Guardados", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
        }
        if (savedTeams.isEmpty()) {
            item { Text("No tienes equipos guardados.", modifier = Modifier.padding(8.dp)) }
        } else {
            items(savedTeams) { savedTeam ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(savedTeam.nombre, modifier = Modifier.weight(1f))
                    Button(onClick = { onAddToLeague(savedTeam) }, enabled = currentLeagueTeams.none { it.nombre == savedTeam.nombre }, modifier = Modifier.padding(horizontal = 4.dp)) { Text("Añadir") }
                    Button(onClick = { onDeleteSavedTeam(savedTeam.nombre) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Borrar") }
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

        // --- Create New Team Section ---
        item { Text("Crear y Guardar Equipo Nuevo", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(16.dp)); OutlinedTextField(value = teamName, onValueChange = { teamName = it }, label = { Text("Nombre del Equipo") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(16.dp)); Text("Plantilla", style = MaterialTheme.typography.titleMedium) }

        itemsIndexed(playersInfo) { index, playerInfo ->
            val isGoalKeeper = index == 4
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { RadioButton(selected = captainIndex == index, onClick = { captainIndex = index }); Text("(C)", style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(value = playerInfo.name, onValueChange = { newName -> val newList = playersInfo.toMutableList(); newList[index] = newList[index].copy(name = newName); playersInfo = newList }, label = { Text(if (isGoalKeeper) "Nombre del Portero" else "Jugador ${index + 1}") }, modifier = Modifier.fillMaxWidth())
                    if (!isGoalKeeper) {
                        var expanded by remember { mutableStateOf(false) }
                        val positions = listOf("DL", "MCO", "MC", "MCD", "DF")
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(value = playerInfo.position, onValueChange = {}, readOnly = true, label = { Text("Posición") }, modifier = Modifier.menuAnchor().fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) })
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                positions.forEach { position -> DropdownMenuItem(text = { Text(position) }, onClick = { val newList = playersInfo.toMutableList(); newList[index] = newList[index].copy(position = position); playersInfo = newList; expanded = false }) }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)); Button(onClick = { if (teamName.isNotBlank()) { val newPlayers = playersInfo.mapIndexed { index, info -> val isPortero = index == 4; val defaultName = if (isPortero) "Portero" else "Jugador ${index + 1}"; val defaultPos = if (isPortero) "POR" else "S/P"; Jugador(nombre = info.name.ifBlank { defaultName }, posicion = if (isPortero) defaultPos else info.position.ifBlank { defaultPos }, isCapitan = captainIndex == index, isPortero = isPortero) }; val newTeam = Equipo(nombre = teamName, jugadores = newPlayers); onSaveNewTeam(newTeam); onAddToLeague(newTeam); teamName = ""; playersInfo = List(5) { PlayerInputState() }; captainIndex = -1 } }, modifier = Modifier.fillMaxWidth()) { Text("Guardar y Añadir a la Liga") }; Spacer(Modifier.height(16.dp)); Text("Equipos en la Liga Actual: ${currentLeagueTeams.size}", style = MaterialTheme.typography.titleMedium) }
        items(currentLeagueTeams) { equipo -> Text("- ${equipo.nombre}", modifier = Modifier.padding(start = 16.dp)) }
        item { Spacer(Modifier.height(16.dp)); Button(onClick = onDone, enabled = currentLeagueTeams.size >= 2, modifier = Modifier.fillMaxWidth()) { Text("Generar Calendario y Volver") } }
    }
}

@Composable
fun CalendarScreen(partidos: List<Partido>, playoffMatches: List<Partido>, onBack: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Calendario", style = MaterialTheme.typography.headlineMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { Text("Liga Regular", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(partidos) { index, partido ->
                val resultado = if (partido.golesLocal != null) "${partido.golesLocal} - ${partido.golesVisitante}" else "Pendiente"
                Text("${index + 1}. ${partido.local.nombre} vs ${partido.visitante.nombre} | $resultado")
            }
            if (playoffMatches.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)); Text("Playoffs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp)) }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Semifinales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            playoffMatches.getOrNull(0)?.let { Text("${it.local.nombre} vs ${it.visitante.nombre} | ${if (it.golesLocal != null) "${it.golesLocal} - ${it.golesVisitante}" else "Pendiente"}") }
                            playoffMatches.getOrNull(1)?.let { Text("${it.local.nombre} vs ${it.visitante.nombre} | ${if (it.golesLocal != null) "${it.golesLocal} - ${it.golesVisitante}" else "Pendiente"}") }
                        }
                    }
                }
                if (playoffMatches.size > 2) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Final", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                playoffMatches.getOrNull(2)?.let { Text("${it.local.nombre} vs ${it.visitante.nombre} | ${if (it.golesLocal != null) "${it.golesLocal} - ${it.golesVisitante}" else "Pendiente"}") }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
fun ClassificationScreen(equipos: List<Equipo>, onBack: () -> Unit) {
    val clasificacion = equipos.sortedWith(compareByDescending<Equipo> { it.puntos }.thenByDescending { it.golesAFavor - it.golesEnContra }.thenByDescending { it.golesAFavor })
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Clasificación", style = MaterialTheme.typography.headlineMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Pos", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
            Text("Equipo", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
            Text("Pts", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("GF", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("GC", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(clasificacion) { index, equipo ->
                val color = when {
                    index < 4 && equipo.puntos > 0 -> Color.Green.copy(alpha = 0.2f)
                    index == clasificacion.lastIndex && clasificacion.size > 1 -> Color.Red.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
                Row(modifier = Modifier.fillMaxWidth().background(color).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", modifier = Modifier.weight(0.5f))
                    Text(equipo.nombre, modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold)
                    Text(equipo.puntos.toString(), modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                    Text(equipo.golesAFavor.toString(), modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                    Text(equipo.golesEnContra.toString(), modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
fun StatsScreen(equipos: List<Equipo>, partidos: List<Partido>, onBack: () -> Unit) {
    val partidosJugados = partidos.filter { it.golesLocal != null }
    val totalGoles = partidosJugados.sumOf { (it.golesLocal ?: 0) + (it.golesVisitante ?: 0) }
    val promedioGoles = if (partidosJugados.isNotEmpty()) totalGoles.toFloat() / partidosJugados.size else 0f
    val equipoMasGoleador = equipos.maxByOrNull { it.golesAFavor }
    val equipoMasGoleado = equipos.maxByOrNull { it.golesEnContra }
    val maximosGoleadores = equipos.flatMap { it.jugadores }.filter { it.goles > 0 }.sortedByDescending { it.goles }.take(5)
    val maximosAsistentes = equipos.flatMap { it.jugadores }.filter { it.asistencias > 0 }.sortedByDescending { it.asistencias }.take(5)
    val mejoresPorteros = equipos.flatMap { it.jugadores }.filter { it.isPortero && it.paradas > 0 }.sortedByDescending { it.paradas }.take(3)

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Estadísticas de la Liga", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Resumen General", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Promedio de goles por partido: ${String.format("%.2f", promedioGoles)}")
                equipoMasGoleador?.let { Text("Equipo más goleador: ${it.nombre} (${it.golesAFavor} goles)") }
                equipoMasGoleado?.let { Text("Equipo más goleado: ${it.nombre} (${it.golesEnContra} goles)") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Máximos Goleadores", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (maximosGoleadores.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(maximosGoleadores) { jugador ->
                            val captainIcon = if (jugador.isCapitan) " (C)" else ""
                            Text("${jugador.nombre}$captainIcon - ${jugador.goles} goles")
                        }
                    }
                } else {
                    Text("Aún no se han marcado goles.")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Máximos Asistentes", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (maximosAsistentes.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(maximosAsistentes) { jugador ->
                            val captainIcon = if (jugador.isCapitan) " (C)" else ""
                            Text("${jugador.nombre}$captainIcon - ${jugador.asistencias} asistencias")
                        }
                    }
                } else {
                    Text("Aún no hay asistencias.")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Porteros con Más Paradas", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (mejoresPorteros.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(mejoresPorteros) { portero ->
                            Text("${portero.nombre} - ${portero.paradas} paradas")
                        }
                    }
                } else {
                    Text("Aún no hay paradas registradas.")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
fun HistoryScreen(history: List<SeasonResult>, onBack: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Historial de Campeones", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (history.isEmpty()) {
            Text("Aún no se han completado temporadas.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(history) { result ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Temporada ${result.seasonNumber}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("🏆 Campeón: ${result.champion}", color = Color(0xFFD4AF37))
                            Text("🥈 Subcampeón: ${result.runnerUp}")
                            Text("⚽ Máximo Goleador: ${result.topScorer.nombre} (${result.topScorer.equipo}) - ${result.topScorer.value} goles${if (result.topScorer.isCapitan) " (C)" else ""}")
                            Text("🅰️ Máximo Asistente: ${result.topAssister.nombre} (${result.topAssister.equipo}) - ${result.topAssister.value} asistencias${if (result.topAssister.isCapitan) " (C)" else ""}")
                            Text("🧤 Portero Top: ${result.topKeeper.nombre} (${result.topKeeper.equipo}) - ${result.topKeeper.value} paradas")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

// --- Logic Functions ---
private fun generarCalendario(equipos: List<Equipo>): List<Partido> {
    val partidos = mutableListOf<Partido>()
    for (i in equipos.indices) {
        for (j in equipos.indices) {
            if (i != j) { partidos.add(Partido(equipos[i], equipos[j])) }
        }
    }
    return partidos.shuffled()
}

private fun actualizarEstadisticas(local: Equipo, visitante: Equipo, golesLocal: Int, golesVisitante: Int): Pair<Equipo, Equipo> {
    var localActualizado = local.copy(golesAFavor = local.golesAFavor + golesLocal, golesEnContra = local.golesEnContra + golesVisitante)
    var visitanteActualizado = visitante.copy(golesAFavor = visitante.golesAFavor + golesVisitante, golesEnContra = visitante.golesEnContra + golesLocal)

    when {
        golesLocal > golesVisitante -> {
            localActualizado = localActualizado.copy(puntos = localActualizado.puntos + 3, ganados = localActualizado.ganados + 1)
            visitanteActualizado = visitanteActualizado.copy(perdidos = visitanteActualizado.perdidos + 1)
        }
        golesLocal < golesVisitante -> {
            visitanteActualizado = visitanteActualizado.copy(puntos = visitanteActualizado.puntos + 3, ganados = visitanteActualizado.ganados + 1)
            localActualizado = localActualizado.copy(perdidos = localActualizado.perdidos + 1)
        }
        else -> {
            localActualizado = localActualizado.copy(puntos = localActualizado.puntos + 1, empatados = localActualizado.empatados + 1)
            visitanteActualizado = visitanteActualizado.copy(puntos = visitanteActualizado.puntos + 1, empatados = visitanteActualizado.empatados + 1)
        }
    }
    return Pair(localActualizado, visitanteActualizado)
}

@Preview(showBackground = true)
@Composable
fun MainMenuPreview() {
    MASTERS3Theme {
        MainMenuScreen(onNavigate = {}, onSimulateMatch = {}, onExit = {}, onStartNewSeason = {}, hasTeams = true, gamePhase = GamePhase.REGULAR_SEASON, lastMatchResult = "Equipo 1 2 - 0 Equipo 2", hasPlayoffMatches = false)
    }
}

package com.example.lea

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

enum class TipoAnexo { NENHUM, IMAGEM, VIDEO, DOC }

private val CardAzul = Color(0xFFD5E3FF)
private val Vermelho = Color(0xFFB71C1C)
private val CorMinha = Color(0xFF2ECC71)
private val CorOutro = Color(0xFF4A8BFF)
private val LinkRegex = Regex("""(https?://|www\.)\S+""")

// ---------- Utilidades ----------
fun nomeDoArquivo(ctx: Context, uri: Uri): String =
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    } ?: "documento"

private fun abrirDocumento(ctx: Context, m: Mensagem) {
    val uri = m.anexo ?: return
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, ctx.contentResolver.getType(uri) ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (e: Exception) { /* nenhum app para abrir este tipo de arquivo */ }
}

private fun plural(n: Int, um: String, varios: String) = "$n ${if (n == 1) um else varios}"

private fun previa(m: Mensagem?): String = when {
    m == null -> "Nenhuma mensagem ainda"
    m.tipo == TipoAnexo.IMAGEM -> "Foto"
    m.tipo == TipoAnexo.VIDEO -> "Vídeo"
    m.tipo == TipoAnexo.DOC -> m.nomeArquivo
    else -> m.texto
}

// ---------- Componentes ----------
@Composable
fun AvatarGrupo(tamanho: Dp, foto: Uri?, icone: ImageVector = Icons.Default.Group) {
    Box(
        Modifier.size(tamanho).clip(CircleShape).background(CardAzul),
        contentAlignment = Alignment.Center
    ) {
        if (foto != null) {
            AsyncImage(
                model = foto, contentDescription = "Foto do grupo",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(icone, null, tint = Primary, modifier = Modifier.size(tamanho * 0.5f))
        }
    }
}

@Composable
private fun VazioGrande(icone: ImageVector, titulo: String, texto: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icone, null, tint = Vermelho, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(12.dp))
        Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(Modifier.height(8.dp))
        Text(texto, fontSize = 12.sp, color = TextDark, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LinhaGrupo(
    icone: ImageVector, texto: String, direita: String? = null, onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFB9CCF5)), contentAlignment = Alignment.Center) {
            Icon(icone, null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark, modifier = Modifier.weight(1f))
        if (direita != null) Text(direita, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
    }
}

// ---------- Lista de grupos ----------
@Composable
fun ListaGruposScreen(estado: AppState, onAbrirGrupo: (String) -> Unit, onCriar: () -> Unit) {
    if (estado.grupos.isEmpty()) {
        EmptyState(
            Icons.Default.Group, "Você ainda não tem nenhum grupo.\nCrie o primeiro!", "Criar grupo", onCriar
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Titulo("Mensagens e Grupos", Modifier.weight(1f))
            IconButton(onClick = onCriar) { Icon(Icons.Default.Add, "Criar grupo", tint = Primary) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(estado.grupos, key = { it.id }) { g ->
                Row(
                    Modifier.fillMaxWidth().clickable { onAbrirGrupo(g.nome) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarGrupo(40.dp, g.foto)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(g.nome, fontWeight = FontWeight.Bold, color = TextDark)
                        Text(
                            previa(estado.chats[g.nome]?.lastOrNull()), fontSize = 10.sp, color = Suave,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ---------- Criar um grupo ----------
@Composable
fun CriarGrupoScreen(estado: AppState, onAdicionarPessoas: () -> Unit, onCriado: (Grupo) -> Unit) {
    // rememberSaveable: não perde o que foi digitado ao ir escolher participantes e voltar
    var foto by rememberSaveable { mutableStateOf<Uri?>(null) }
    var nome by rememberSaveable { mutableStateOf("") }
    var descricao by rememberSaveable { mutableStateOf("") }
    val escolherFoto = rememberSeletorDeImagem { foto = it }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Titulo("Criar um grupo")
        Spacer(Modifier.height(16.dp))
        Box(Modifier.clickable { escolherFoto() }) { AvatarGrupo(88.dp, foto, Icons.Default.CameraAlt) }
        TextButton(onClick = escolherFoto) {
            Text(if (foto == null) "Adicionar foto" else "Trocar foto", color = Primary)
        }
        OutlinedTextField(
            value = nome, onValueChange = { nome = it }, label = { Text("Nome do grupo") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = descricao, onValueChange = { descricao = it }, label = { Text("Descrição") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Text("Adicionar participantes", fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(Modifier.height(8.dp))
        estado.rascunhoMembros.forEach { id ->
            AssistChip(
                onClick = { estado.rascunhoMembros.remove(id) },
                label = { Text(estado.nomeDoUsuario(id)) },
                trailingIcon = { Icon(Icons.Default.Close, "Remover", modifier = Modifier.size(16.dp)) }
            )
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(CardAzul).clickable { onAdicionarPessoas() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Add, "Adicionar participantes", tint = Primary) }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onCriado(estado.criarGrupoCompleto(nome.trim(), descricao.trim(), foto)) },
            enabled = nome.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) { Text("Criar grupo") }
    }
}

// ---------- Adicionar participantes ----------
@Composable
fun AdicionarParticipantesScreen(estado: AppState, onFechar: () -> Unit) {
    val alvo = estado.alvoParticipantes            // null = grupo que ainda não foi criado
    val membros = estado.membrosDe(alvo)
    var convite by remember { mutableStateOf("") }
    var conviteEnviado by remember { mutableStateOf("") }
    var copiado by remember { mutableStateOf(false) }
    var busca by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val link = "https://lea.app/convite/${alvo ?: "novo"}"   // link de exemplo: precisa de servidor para valer
    val amigos = estado.seguindo.map { it to estado.nomeDoUsuario(it) }
        .filter { busca.isBlank() || it.second.contains(busca, ignoreCase = true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFechar) { Icon(Icons.Default.Close, "Fechar", tint = TextDark) }
            Titulo("Adicionar Participantes")
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardAzul).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Link, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Convide via link", fontWeight = FontWeight.Bold, color = TextDark)
                Text("Qualquer pessoa com o link pode entrar", fontSize = 10.sp, color = TextDark)
            }
            Button(
                onClick = { clipboard.setText(AnnotatedString(link)); copiado = true },
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text(if (copiado) "Copiado!" else "Copiar", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Adicionar por e-mail ou nome de usuário", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDark)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = convite, onValueChange = { convite = it }, placeholder = { Text("E-mail ou Nome de Usuário") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        // O convite é simulado: enviar de verdade exige um servidor
        Button(
            onClick = { conviteEnviado = convite.trim(); convite = "" },
            enabled = convite.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) { Text("Enviar convite direto") }
        if (conviteEnviado.isNotBlank()) {
            Text("Convite enviado para $conviteEnviado", fontSize = 11.sp, color = CorMinha, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardAzul).padding(12.dp)) {
            Text("Amizades", fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = busca, onValueChange = { busca = it },
                placeholder = { Text("Buscar por nome de usuário ou e-mail") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
            )
            Spacer(Modifier.height(8.dp))
            if (estado.seguindo.isEmpty()) {
                Text("Você ainda não segue ninguém. Siga pessoas para poder adicioná-las aos grupos.", fontSize = 11.sp, color = TextDark)
            }
            amigos.forEach { (id, nome) ->
                val jaEsta = membros.contains(id)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(nome, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark, modifier = Modifier.weight(1f))
                    Button(
                        onClick = { estado.adicionarMembro(alvo, id) }, enabled = !jaEsta,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text(if (jaEsta) "Adicionado" else "+ Adicionar", fontSize = 11.sp) }
                }
            }
        }
    }
}

// ---------- Ver participantes do grupo ----------
@Composable
fun InfoGrupoScreen(
    estado: AppState, onVoltar: () -> Unit, onMidia: () -> Unit, onAdicionar: (Int) -> Unit
) {
    val grupo = estado.grupos.find { it.nome == estado.chatAberto }
    if (grupo == null) {
        EmptyState(Icons.Default.Group, "Grupo não encontrado.")
        return
    }
    var editandoDescricao by remember { mutableStateOf(false) }
    var textoDescricao by remember { mutableStateOf("") }
    var verMudancas by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }
    var busca by remember { mutableStateOf("") }
    val membros = grupo.membros.filter {
        busca.isBlank() || estado.nomeDoUsuario(it).contains(busca, ignoreCase = true)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextDark) }
        }
        AvatarGrupo(80.dp, grupo.foto)
        Spacer(Modifier.height(8.dp))
        Titulo(grupo.nome)
        Text("Grupo · ${plural(grupo.membros.size + 1, "membro", "membros")}", fontSize = 11.sp, color = TextDark)
        Text(
            if (grupo.descricao.isBlank()) "Adicionar descrição do Grupo" else grupo.descricao,
            fontSize = 11.sp, color = if (grupo.descricao.isBlank()) Primary else TextDark,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable { textoDescricao = grupo.descricao; editandoDescricao = true }.padding(8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {}) { Text("Membros") }
            OutlinedButton(onClick = onMidia) { Text("Mídia") }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${grupo.membros.size + 1} Membros", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = TextDark, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { buscando = !buscando; busca = "" }) {
                Icon(Icons.Default.Search, "Buscar membro", tint = TextDark)
            }
        }
        if (buscando) {
            OutlinedTextField(
                value = busca, onValueChange = { busca = it }, placeholder = { Text("Buscar membro") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardAzul)) {
            LinhaGrupo(Icons.Default.Add, "Adicionar Membros") { onAdicionar(grupo.id) }
            LinhaGrupo(Icons.Default.Person, "Você", "Admin")
            membros.forEach { LinhaGrupo(Icons.Default.Person, estado.nomeDoUsuario(it), "Membro") }
            LinhaGrupo(Icons.Default.Description, "Mostrar mudanças de membros") { verMudancas = true }
        }
    }

    if (editandoDescricao) {
        AlertDialog(
            onDismissRequest = { editandoDescricao = false },
            title = { Text("Descrição do grupo") },
            text = { OutlinedTextField(value = textoDescricao, onValueChange = { textoDescricao = it }, minLines = 2) },
            confirmButton = {
                TextButton(onClick = {
                    estado.atualizarDescricao(grupo.id, textoDescricao.trim()); editandoDescricao = false
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editandoDescricao = false }) { Text("Cancelar") } }
        )
    }
    if (verMudancas) {
        AlertDialog(
            onDismissRequest = { verMudancas = false },
            title = { Text("Mudanças de membros") },
            text = {
                if (grupo.historico.isEmpty()) Text("Nenhuma mudança ainda.")
                else Column { grupo.historico.reversed().forEach { Text("• $it", fontSize = 13.sp) } }
            },
            confirmButton = { TextButton(onClick = { verMudancas = false }) { Text("Fechar") } }
        )
    }
}

// ---------- Ver mídias do grupo (Mídia / Links / Docs) ----------
@Composable
fun MidiasGrupoScreen(estado: AppState, onVoltar: () -> Unit, onVerMensagem: () -> Unit) {
    val nome = estado.chatAberto
    val msgs = estado.chats[nome]
    if (msgs == null) {
        EmptyState(Icons.Default.Group, "Conversa não encontrada.")
        return
    }
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var aba by remember { mutableStateOf(0) }
    var selecionando by remember { mutableStateOf(false) }
    val marcadas = remember { mutableStateListOf<Int>() }       // posições das mensagens marcadas

    val midias = msgs.withIndex().filter { it.value.tipo == TipoAnexo.IMAGEM || it.value.tipo == TipoAnexo.VIDEO }
    val docs = msgs.withIndex().filter { it.value.tipo == TipoAnexo.DOC }
    val links = msgs.withIndex().flatMap { iv -> LinkRegex.findAll(iv.value.texto).map { iv.index to it.value }.toList() }

    fun alternar(i: Int) { if (marcadas.contains(i)) marcadas.remove(i) else marcadas.add(i) }
    fun sair() { selecionando = false; marcadas.clear() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextDark) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Mídia", "Links", "Docs").forEachIndexed { i, t ->
                    FilterChip(selected = aba == i, onClick = { aba = i; sair() }, label = { Text(t, fontSize = 11.sp) })
                }
            }
            TextButton(onClick = { if (selecionando) sair() else selecionando = true }) {
                Text(if (selecionando) "Cancelar" else "Selecionar", fontSize = 12.sp, color = TextDark)
            }
        }

        Box(Modifier.weight(1f)) {
            when (aba) {
                0 -> if (midias.isEmpty()) {
                    VazioGrande(Icons.Default.PhotoLibrary, "Nenhuma mídia", "Fotos e Vídeos compartilhados no grupo \"$nome\" aparecerão aqui")
                } else Column {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(midias) { iv ->
                            Box(
                                Modifier.aspectRatio(1f).background(Suave)
                                    .clickable { if (selecionando) alternar(iv.index) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (iv.value.tipo == TipoAnexo.IMAGEM) {
                                    AsyncImage(
                                        model = iv.value.anexo, contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                    )
                                } else Icon(Icons.Default.PlayArrow, "Vídeo", tint = Color.White, modifier = Modifier.size(36.dp))
                                if (marcadas.contains(iv.index)) {
                                    Box(Modifier.fillMaxSize().background(Primary.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, null, tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    val fotos = midias.count { it.value.tipo == TipoAnexo.IMAGEM }
                    val videos = midias.size - fotos
                    Text(
                        listOfNotNull(
                            if (fotos > 0) plural(fotos, "foto", "fotos") else null,
                            if (videos > 0) plural(videos, "vídeo", "vídeos") else null
                        ).joinToString(", "),
                        fontSize = 11.sp, color = TextDark, modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center
                    )
                }

                1 -> if (links.isEmpty()) {
                    VazioGrande(Icons.Default.Link, "Nenhum link", "Links enviados e recebidos no grupo \"$nome\" aparecerão nesta tela.")
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(links) { (indice, url) ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(CardAzul)) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp).clickable {
                                    if (selecionando) alternar(indice)
                                    else try { uriHandler.openUri(if (url.startsWith("www.")) "https://$url" else url) } catch (e: Exception) {}
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selecionando) Checkbox(checked = marcadas.contains(indice), onCheckedChange = { alternar(indice) })
                                else Icon(Icons.Default.Link, null, tint = TextDark)
                                Spacer(Modifier.width(10.dp))
                                Text(url, fontSize = 11.sp, color = TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row(
                                Modifier.fillMaxWidth().background(Color(0xFFB9CCF5)).clickable { onVerMensagem() }.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ver mensagem", fontSize = 10.sp, color = TextDark, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, null, tint = TextDark, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    item {
                        Text(plural(links.size, "link", "links"), fontSize = 11.sp, color = TextDark,
                            modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center)
                    }
                }

                else -> if (docs.isEmpty()) {
                    VazioGrande(Icons.Default.Description, "Nenhum documento", "Documentos compartilhados no grupo \"$nome\" aparecerão aqui")
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(docs) { iv ->
                        val ehPdf = iv.value.nomeArquivo.endsWith(".pdf", ignoreCase = true)
                        Row(
                            Modifier.fillMaxWidth().clickable { if (selecionando) alternar(iv.index) else abrirDocumento(ctx, iv.value) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selecionando) Checkbox(checked = marcadas.contains(iv.index), onCheckedChange = { alternar(iv.index) })
                            Icon(
                                if (ehPdf) Icons.Default.PictureAsPdf else Icons.Default.Description, null,
                                tint = if (ehPdf) Vermelho else Color.DarkGray, modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(iv.value.nomeArquivo, fontSize = 12.sp, color = TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item {
                        Text(plural(docs.size, "documento", "documentos"), fontSize = 11.sp, color = TextDark,
                            modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        if (selecionando && marcadas.isNotEmpty()) {
            Button(
                onClick = {
                    // apaga de trás para frente para as posições não se desencontrarem
                    marcadas.sortedDescending().forEach { msgs.removeAt(it) }
                    sair()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Vermelho)
            ) { Text("Excluir (${marcadas.size})") }
        }
    }
}

// ---------- Conversa (agora com foto, vídeo e documento) ----------
@Composable
fun ChatConversaScreen(estado: AppState, onVoltar: () -> Unit, onInfoGrupo: () -> Unit) {
    val msgs = estado.chats[estado.chatAberto]
    if (msgs == null) {
        EmptyState(Icons.Default.Group, "Conversa não encontrada.")
        return
    }
    val ctx = LocalContext.current
    val ehGrupo = estado.grupos.any { it.nome == estado.chatAberto }
    var texto by remember { mutableStateOf("") }
    var menuAnexo by remember { mutableStateOf(false) }
    val lista = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) lista.animateScrollToItem(msgs.size - 1) }

    val escolherMidia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val video = ctx.contentResolver.getType(uri)?.startsWith("video") == true
            msgs.add(Mensagem("", true, uri, "", if (video) TipoAnexo.VIDEO else TipoAnexo.IMAGEM))
        }
    }
    val escolherDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) msgs.add(Mensagem("", true, uri, nomeDoArquivo(ctx, uri), TipoAnexo.DOC))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextDark) }
            Row(
                Modifier.weight(1f).then(if (ehGrupo) Modifier.clickable { onInfoGrupo() } else Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(estado.chatAberto, fontSize = 18.sp, color = TextDark)
                if (ehGrupo) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Info, "Informações do grupo", tint = Suave, modifier = Modifier.size(18.dp))
                }
            }
        }
        LazyColumn(Modifier.weight(1f), state = lista, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(msgs) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.enviadaPorMim) Arrangement.End else Arrangement.Start) {
                    val forma = RoundedCornerShape(10.dp)
                    Column(
                        Modifier.widthIn(max = 260.dp).clip(forma).background(Color.White)
                            .border(1.dp, if (m.enviadaPorMim) CorMinha else CorOutro, forma).padding(10.dp)
                    ) {
                        when (m.tipo) {
                            TipoAnexo.IMAGEM -> AsyncImage(
                                model = m.anexo, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(6.dp))
                            )
                            TipoAnexo.VIDEO -> Box(
                                Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(6.dp)).background(Suave),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.PlayArrow, "Vídeo", tint = Color.White, modifier = Modifier.size(40.dp)) }
                            TipoAnexo.DOC -> Row(
                                Modifier.clickable { abrirDocumento(ctx, m) }, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, null, tint = TextDark)
                                Spacer(Modifier.width(6.dp))
                                Text(m.nomeArquivo, fontSize = 12.sp, color = TextDark)
                            }
                            TipoAnexo.NENHUM -> {}
                        }
                        if (m.texto.isNotBlank()) Text(m.texto, fontSize = 12.sp, color = TextDark)
                    }
                }
            }
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { menuAnexo = true }) { Icon(Icons.Default.AttachFile, "Anexar", tint = TextDark) }
                DropdownMenu(expanded = menuAnexo, onDismissRequest = { menuAnexo = false }) {
                    DropdownMenuItem(text = { Text("Foto ou vídeo") }, onClick = {
                        menuAnexo = false
                        escolherMidia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    })
                    DropdownMenuItem(text = { Text("Documento") }, onClick = {
                        menuAnexo = false
                        escolherDoc.launch(arrayOf("*/*"))
                    })
                }
            }
            OutlinedTextField(
                value = texto, onValueChange = { texto = it }, placeholder = { Text("Digite algo") },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
            )
            IconButton(onClick = {
                if (texto.isNotBlank()) { msgs.add(Mensagem(texto.trim(), true)); texto = "" }
            }) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Primary) }
        }
    }
}
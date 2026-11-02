package com.example.lea

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation

// ---------- Cores ----------
val Primary = Color(0xFF6C7FE8)
val Bg = Color(0xFFE6E6E8)
val BarColor = Color(0xFFB4B8C4)
val TextDark = Color(0xFF5A6078)
val Suave = Color(0xFF8A8F9E)
private val Verde = Color(0xFF2ECC71)

// ---------- Modelos de dados ----------
data class Post(
    val id: Int, val titulo: String, val descricao: String, val imagem: Uri?,
    val autorId: String = "eu",   // "eu" = você; com vários usuários será o id de cada pessoa
    val autorNome: String = "",
    val autorFoto: Uri? = null
)
data class Grupo(
    val id: Int, val nome: String, val descricao: String = "", val foto: Uri? = null,
    val membros: List<String> = emptyList(),      // ids das outras pessoas (você é sempre admin)
    val historico: List<String> = emptyList()
)

data class Mensagem(
    val texto: String,
    val enviadaPorMim: Boolean,
    val anexo: Uri? = null,
    val nomeArquivo: String = "",
    val tipo: TipoAnexo = TipoAnexo.NENHUM
)

/** secao: "Importante", "Hoje" ou "Esta semana" */
data class Notificacao(val texto: String, val secao: String, val mencao: Boolean)

// ---------- Estado do app (fica na memória enquanto o app está aberto) ----------
class AppState {
    // Perfil: tudo começa vazio e o usuário preenche
    var nomeUsuario by mutableStateOf("")      // como quer ser chamado(a)
    var fotoPerfil by mutableStateOf<Uri?>(null)
    var nomeCompleto by mutableStateOf("")
    var email by mutableStateOf("")
    var telefone by mutableStateOf("")
    var sobreMim by mutableStateOf("")

    /** Nome que aparece nos posts. */
    val nomeParaPosts: String get() = nomeUsuario.ifBlank { "Anônimo" }

    var postAbertoId by mutableStateOf<Int?>(null)

    val meuId = "eu"
    var perfilAbertoId by mutableStateOf("")

    fun nomeDoAutor(p: Post) = if (p.autorId == meuId) nomeParaPosts else p.autorNome.ifBlank { "Usuário" }
    val seguindo: SnapshotStateList<String> = mutableStateListOf()   // ids de quem você segue

    fun alternarSeguir(id: String) {
        if (seguindo.contains(id)) seguindo.remove(id) else seguindo.add(id)
    }
    fun fotoDoAutor(p: Post) = if (p.autorId == meuId) fotoPerfil else p.autorFoto

    var chatAberto by mutableStateOf("")
    val chats = mutableStateMapOf<String, SnapshotStateList<Mensagem>>()
    val notificacoes: SnapshotStateList<Notificacao> = mutableStateListOf()

    fun abrirChat(nome: String) {
        if (!chats.containsKey(nome)) chats[nome] = mutableStateListOf()
        chatAberto = nome
    }

    var alvoParticipantes by mutableStateOf<Int?>(null)   // grupo que recebe participantes (null = ainda não criado)
    val rascunhoMembros: SnapshotStateList<String> = mutableStateListOf()

    fun nomeDoUsuario(id: String) =
        posts.firstOrNull { it.autorId == id }?.autorNome?.ifBlank { null } ?: "Usuário"

    fun membrosDe(grupoId: Int?): List<String> =
        if (grupoId == null) rascunhoMembros else grupos.find { it.id == grupoId }?.membros ?: emptyList()

    fun adicionarMembro(grupoId: Int?, usuarioId: String) {
        if (grupoId == null) {
            if (!rascunhoMembros.contains(usuarioId)) rascunhoMembros.add(usuarioId)
            return
        }
        val i = grupos.indexOfFirst { it.id == grupoId }
        if (i < 0 || grupos[i].membros.contains(usuarioId)) return
        val g = grupos[i]
        grupos[i] = g.copy(
            membros = g.membros + usuarioId,
            historico = g.historico + "${nomeDoUsuario(usuarioId)} foi adicionado(a) ao grupo"
        )
    }

    fun criarGrupoCompleto(nome: String, descricao: String, foto: Uri?): Grupo {
        val g = Grupo(
            proximoId++, nome, descricao, foto, rascunhoMembros.toList(),
            rascunhoMembros.map { "${nomeDoUsuario(it)} foi adicionado(a) ao grupo" }
        )
        grupos.add(g)
        rascunhoMembros.clear()
        return g
    }

    fun atualizarDescricao(grupoId: Int, descricao: String) {
        val i = grupos.indexOfFirst { it.id == grupoId }
        if (i >= 0) grupos[i] = grupos[i].copy(descricao = descricao)
    }

    val posts: SnapshotStateList<Post> = mutableStateListOf()
    val grupos: SnapshotStateList<Grupo> = mutableStateListOf()
    val salvos: SnapshotStateList<Int> = mutableStateListOf()

    private var proximoId = 1

    // Post de teste de OUTRA pessoa (apague esta linha quando não precisar mais testar)
    init { posts.add(Post(999, "Post de teste", "Escrito por outra pessoa", null, "outro", "Nanda_do_grau")) }

    fun publicar(titulo: String, descricao: String, imagem: Uri?) {
        posts.add(0, Post(proximoId++, titulo, descricao, imagem))
    }

    fun alternarSalvo(id: Int) {
        if (salvos.contains(id)) salvos.remove(id) else salvos.add(id)
    }
}

// ---------- Activity ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LeaApp() } }
    }
}

// ---------- Navegação ----------
@Composable
fun LeaApp() {
    val nav = rememberNavController()
    val estado = remember { AppState() }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val mostrarBarras = route !in listOf("login", "signup", "forgot", "code", "newpass")

    Scaffold(
        containerColor = Bg,
        topBar = { if (mostrarBarras) TopBar(nav, estado) },
        bottomBar = {
            if (mostrarBarras) BottomBar(
                nav,
                if (route in listOf("creategroup", "addpeople", "groupinfo", "groupmedia")) "groups" else route
            )
        }
    ) { padding ->
        NavHost(nav, startDestination = "login", modifier = Modifier.padding(padding)) {
            composable("login") {
                LoginScreen(
                    onEntrar = { email ->
                        estado.email = email
                        nav.navigate("home") { popUpTo("login") { inclusive = true } }
                    },
                    onEsqueceu = { nav.navigate("forgot") },
                    onRegistrar = { nav.navigate("signup") }
                )
            }
            composable("signup") {
                SignupScreen(estado) { nav.navigate("home") { popUpTo("login") { inclusive = true } } }
            }
            composable("forgot") { ForgotScreen { nav.navigate("code") } }
            composable("code") { CodeScreen { nav.navigate("newpass") } }
            composable("newpass") {
                NewPasswordScreen { nav.navigate("login") { popUpTo("login") { inclusive = true } } }
            }
            composable("home") {
                HomeScreen(
                    estado,
                    onBuscar = { nav.navigate("search") },
                    onPostar = { nav.navigate("post") },
                    onAbrirPost = { estado.postAbertoId = it; nav.navigate("detail") }
                )
            }
            composable("detail") {
                DetailScreen(
                    estado,
                    onAbrirAutor = { post ->
                        if (post.autorId == estado.meuId) nav.navigate("profile")
                        else { estado.perfilAbertoId = post.autorId; nav.navigate("authorprofile") }
                    },
                    onMensagem = { post ->
                        estado.abrirChat(estado.nomeDoAutor(post))
                        nav.navigate("chat")
                    }
                )
            }
            composable("authorprofile") {
                val doAutor = estado.posts.filter { it.autorId == estado.perfilAbertoId }
                val nome = doAutor.firstOrNull()?.let { estado.nomeDoAutor(it) } ?: "Usuário"
                PerfilScreen(
                    nome = nome,
                    foto = doAutor.firstOrNull()?.let { estado.fotoDoAutor(it) },
                    posts = doAutor,
                    ehProprio = false,
                    seguidores = if (estado.seguindo.contains(estado.perfilAbertoId)) 1 else 0,
                    seguindo = estado.seguindo.contains(estado.perfilAbertoId),
                    onSeguir = { estado.alternarSeguir(estado.perfilAbertoId) },
                    onMensagem = { estado.abrirChat(nome); nav.navigate("chat") },
                    onAbrirPost = { estado.postAbertoId = it; nav.navigate("detail") }
                )
            }
            composable("search") {
                SearchScreen(estado) { estado.postAbertoId = it; nav.navigate("detail") }
            }
            composable("profile") {
                PerfilScreen(
                    nome = estado.nomeUsuario.ifBlank { "Usuário" },
                    foto = estado.fotoPerfil,
                    posts = estado.posts.filter { it.autorId == estado.meuId },
                    ehProprio = true,
                    seguindoQtd = estado.seguindo.size,
                    onNovoPost = { nav.navigate("post") },
                    onEditar = { nav.navigate("editprofile") },
                    onAbrirPost = { estado.postAbertoId = it; nav.navigate("detail") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    estado,
                    onEditar = { nav.navigate("editprofile") },
                    onSalvos = { nav.navigate("saved") },
                    onNotificacoes = { nav.navigate("notifications") },
                    onSair = { nav.navigate("login") { popUpTo(0) { inclusive = true } } }
                )
            }
            composable("notifications") { NotificationsScreen(estado) { nav.popBackStack() } }
            composable("editprofile") { EditProfileScreen(estado) { nav.popBackStack() } }
            composable("saved") {
                SavedScreen(estado) { estado.postAbertoId = it; nav.navigate("detail") }
            }
            composable("groups") {
                ListaGruposScreen(
                    estado,
                    onAbrirGrupo = { nome -> estado.abrirChat(nome); nav.navigate("chat") },
                    onCriar = { estado.rascunhoMembros.clear(); nav.navigate("creategroup") }
                )
            }
            composable("chat") {
                ChatConversaScreen(
                    estado,
                    onVoltar = { nav.popBackStack() },
                    onInfoGrupo = { nav.navigate("groupinfo") }
                )
            }
            composable("creategroup") {
                CriarGrupoScreen(
                    estado,
                    onAdicionarPessoas = { estado.alvoParticipantes = null; nav.navigate("addpeople") },
                    onCriado = { g -> estado.abrirChat(g.nome); nav.navigate("chat") { popUpTo("groups") } }
                )
            }
            composable("addpeople") { AdicionarParticipantesScreen(estado) { nav.popBackStack() } }
            composable("groupinfo") {
                InfoGrupoScreen(
                    estado,
                    onVoltar = { nav.popBackStack() },
                    onMidia = { nav.navigate("groupmedia") },
                    onAdicionar = { id -> estado.alvoParticipantes = id; nav.navigate("addpeople") }
                )
            }
            composable("groupmedia") {
                MidiasGrupoScreen(
                    estado,
                    onVoltar = { nav.popBackStack() },
                    onVerMensagem = { nav.popBackStack("chat", false) }
                )
            }
            composable("post") {
                NewPostScreen(estado) {
                    nav.navigate("home") { popUpTo("home") { inclusive = true } }
                }
            }
            composable("chats") { ChatsListScreen(estado) { nav.navigate("chat") } }
        }
    }
}

// ---------- Barras ----------
@Composable
fun TopBar(nav: NavController, estado: AppState) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clickable { nav.navigate("profile") }) { Avatar(32.dp, estado.fotoPerfil) }
        Spacer(Modifier.width(8.dp))
        if (estado.nomeUsuario.isBlank()) {
            Text(
                "Toque para definir seu nome", fontSize = 13.sp, color = Suave,
                modifier = Modifier.weight(1f).clickable { nav.navigate("editprofile") }
            )
        } else {
            Text(estado.nomeUsuario, fontSize = 13.sp, color = TextDark, modifier = Modifier.weight(1f))
        }
        IconButton(onClick = { nav.navigate("settings") }) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextDark)
        }
    }
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun BottomBar(nav: NavController, atual: String?) {
    val itens = listOf(
        NavItem("home", "Home", Icons.Default.Home),
        NavItem("groups", "Grupos", Icons.Default.Group),
        NavItem("post", "Post", Icons.Default.Add),
        NavItem("chats", "Chats", Icons.Default.Email),
        NavItem("settings", "Config.", Icons.Default.Settings),
    )
    NavigationBar(containerColor = BarColor) {
        itens.forEach { item ->
            NavigationBarItem(
                selected = atual == item.route,
                onClick = {
                    nav.navigate(item.route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color(0xFF4A4F63),
                    unselectedTextColor = Color(0xFF4A4F63),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

// ---------- Componentes reutilizáveis ----------
/** Foto de perfil redonda. Sem foto, mostra o ícone padrão. */
@Composable
fun Avatar(tamanho: Dp, foto: Uri? = null) {
    Box(
        Modifier.size(tamanho).clip(CircleShape).background(Primary),
        contentAlignment = Alignment.Center
    ) {
        if (foto != null) {
            AsyncImage(
                model = foto, contentDescription = "Foto de perfil",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(tamanho * 0.6f))
        }
    }
}

@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier) =
    Text(texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark, modifier = modifier)

/** Tela/área vazia com mensagem e, se quiser, um botão de ação. */
@Composable
fun EmptyState(
    icone: ImageVector,
    mensagem: String,
    textoBotao: String? = null,
    onClick: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icone, null, tint = Suave, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(mensagem, color = TextDark, textAlign = TextAlign.Center)
        if (textoBotao != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                Text(textoBotao)
            }
        }
    }
}

@Composable
fun PostCard(post: Post, autor: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // A imagem só aparece se a pessoa tiver escolhido uma
            if (post.imagem != null) {
                AsyncImage(
                    model = post.imagem, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(170.dp)
                )
            }
            Column(Modifier.padding(12.dp)) {
                Text(post.titulo, fontWeight = FontWeight.Bold, color = TextDark)
                if (post.descricao.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        post.descricao, fontSize = 12.sp, color = TextDark,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("por $autor", fontSize = 10.sp, color = Suave)
            }
        }
    }
}

/** Abre a galeria para escolher UMA imagem. Não precisa de permissão. */
@Composable
fun rememberSeletorDeImagem(aoEscolher: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) aoEscolher(uri)
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

// ---------- 1. Login ----------
@Composable
fun LoginScreen(onEntrar: (String) -> Unit, onEsqueceu: () -> Unit, onRegistrar: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Book, null, tint = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text("LÊA", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, placeholder = { Text("name@example.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = senha, onValueChange = { senha = it },
            label = { Text("Senha") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onEntrar(email.trim()) }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) { Text("Entrar") }

        TextButton(onClick = onEsqueceu) { Text("Esqueceu a senha?", fontSize = 11.sp, color = TextDark) }
        Text("ou entre com", fontSize = 11.sp, color = TextDark)
        IconButton(onClick = {}) { Icon(Icons.Default.Email, null, tint = Suave, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onRegistrar) { Text("Novo/a aqui? Registre-se.", fontSize = 11.sp, color = TextDark) }
    }
}

// ---------- 2. Home ----------
@Composable
fun HomeScreen(
    estado: AppState,
    onBuscar: () -> Unit,
    onPostar: () -> Unit,
    onAbrirPost: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)
                .clickable { onBuscar() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Suave)
            Spacer(Modifier.width(8.dp))
            Text("Pesquisar", color = Suave)
        }
        Spacer(Modifier.height(8.dp))

        if (estado.posts.isEmpty()) {
            EmptyState(
                Icons.Outlined.Book,
                "Ainda não há nenhum post por aqui.\nQue tal ser a primeira pessoa a postar?",
                "Criar post",
                onPostar
            )
        } else {
            Text("${estado.posts.size} resultados", fontSize = 10.sp, color = TextDark)
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(estado.posts, key = { it.id }) { post ->
                    PostCard(post, estado.nomeDoAutor(post)) { onAbrirPost(post.id) }
                }
            }
        }
    }
}

// ---------- 3. Detalhe do post ----------
@Composable
fun DetailScreen(estado: AppState, onAbrirAutor: (Post) -> Unit, onMensagem: (Post) -> Unit) {
    val post = estado.posts.find { it.id == estado.postAbertoId }
    if (post == null) {
        EmptyState(Icons.Outlined.Book, "Post não encontrado.")
        return
    }
    val salvo = estado.salvos.contains(post.id)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Titulo(post.titulo)
        Spacer(Modifier.height(12.dp))
        if (post.imagem != null) {
            AsyncImage(
                model = post.imagem, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(12.dp))
        }
        if (post.descricao.isNotBlank()) {
            Text(post.descricao, fontSize = 13.sp, color = TextDark)
            Spacer(Modifier.height(20.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LinkAcao(Icons.Default.Person, estado.nomeDoAutor(post)) { onAbrirAutor(post) }
            LinkAcao(Icons.Default.Email, "Enviar mensagem") { onMensagem(post) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LinkAcao(Icons.Default.Share, "Compartilhar") {}
            LinkAcao(
                if (salvo) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                if (salvo) "Salvo" else "Salvar"
            ) { estado.alternarSalvo(post.id) }
        }
    }
}

@Composable
private fun LinkAcao(icone: ImageVector, texto: String, onClick: () -> Unit) {
    Row(Modifier.clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, null, tint = TextDark, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(texto, fontSize = 12.sp, color = TextDark)
    }
}

// ---------- 4. Procurar ----------
@Composable
fun SearchScreen(estado: AppState, onAbrirPost: (Int) -> Unit) {
    var busca by remember { mutableStateOf("") }
    val resultados = if (busca.isBlank()) emptyList()
    else estado.posts.filter {
        it.titulo.contains(busca, ignoreCase = true) || it.descricao.contains(busca, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Titulo("Procurando por algo em específico?")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = busca, onValueChange = { busca = it },
            placeholder = { Text("Digite aqui...") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        when {
            busca.isBlank() -> {}
            resultados.isEmpty() -> Text("Nada encontrado para \"$busca\".", color = TextDark)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(resultados, key = { it.id }) { post ->
                    PostCard(post, estado.nomeDoAutor(post)) { onAbrirPost(post.id) }
                }
            }
        }
    }
}

// ---------- 5. Configurações ----------
@Composable
fun SettingsScreen(
    estado: AppState, onEditar: () -> Unit, onSalvos: () -> Unit,
    onNotificacoes: () -> Unit, onSair: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.clickable { onEditar() }) { Avatar(96.dp, estado.fotoPerfil) }
        Spacer(Modifier.height(8.dp))

        if (estado.nomeUsuario.isBlank()) {
            Text("Você ainda não definiu seu nome", color = Suave)
            TextButton(onClick = onEditar) { Text("Completar perfil", color = Primary) }
        } else {
            Titulo(estado.nomeUsuario)
            if (estado.sobreMim.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(estado.sobreMim, fontSize = 12.sp, color = TextDark, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(24.dp))

        MenuItem(Icons.Outlined.Person, "Editar perfil", onEditar)
        MenuItem(Icons.Outlined.Notifications, "Notificações", onNotificacoes)
        MenuItem(Icons.Outlined.FavoriteBorder, "Salvos", onSalvos)
        MenuItem(Icons.Outlined.Close, "Sair", onSair)
    }
}

@Composable
private fun MenuItem(icone: ImageVector, texto: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, null, tint = TextDark)
            Spacer(Modifier.width(12.dp))
            Text(texto, fontWeight = FontWeight.SemiBold, color = TextDark)
        }
        HorizontalDivider(color = Color(0xFFCFD2DA))
    }
}

// ---------- 5.1 Editar perfil ----------
@Composable
fun EditProfileScreen(estado: AppState, onConcluido: () -> Unit) {
    // Cópias locais: só vão para o estado do app quando a pessoa tocar em "Salvar"
    var foto by remember { mutableStateOf(estado.fotoPerfil) }
    var apelido by remember { mutableStateOf(estado.nomeUsuario) }
    var completo by remember { mutableStateOf(estado.nomeCompleto) }
    var email by remember { mutableStateOf(estado.email) }
    var telefone by remember { mutableStateOf(estado.telefone) }
    var sobre by remember { mutableStateOf(estado.sobreMim) }

    val escolherFoto = rememberSeletorDeImagem { foto = it }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.clickable { escolherFoto() }, contentAlignment = Alignment.BottomEnd) {
            Avatar(110.dp, foto)
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.CameraAlt, "Alterar foto", tint = TextDark, modifier = Modifier.size(18.dp)) }
        }
        Row {
            TextButton(onClick = escolherFoto) { Text(if (foto == null) "Adicionar foto" else "Trocar foto", color = Primary) }
            if (foto != null) {
                TextButton(onClick = { foto = null }) { Text("Remover", color = Suave) }
            }
        }
        Spacer(Modifier.height(8.dp))

        Titulo("Seus dados", Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        CampoPerfil(apelido, { apelido = it }, "Como quer ser chamado(a)")
        CampoPerfil(completo, { completo = it }, "Nome completo")
        CampoPerfil(email, { email = it }, "Email", KeyboardType.Email)
        CampoPerfil(telefone, { telefone = it }, "Telefone", KeyboardType.Phone)
        OutlinedTextField(
            value = sobre, onValueChange = { sobre = it },
            label = { Text("Sobre mim") }, minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                estado.fotoPerfil = foto
                estado.nomeUsuario = apelido.trim()
                estado.nomeCompleto = completo.trim()
                estado.email = email.trim()
                estado.telefone = telefone.trim()
                estado.sobreMim = sobre.trim()
                onConcluido()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) { Text("Salvar") }
    }
}

@Composable
private fun CampoPerfil(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    teclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor, onValueChange = aoMudar,
        label = { Text(rotulo) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
}

// ---------- 6. Posts salvos ----------
@Composable
fun SavedScreen(estado: AppState, onAbrirPost: (Int) -> Unit) {
    val salvos = estado.posts.filter { estado.salvos.contains(it.id) }

    if (salvos.isEmpty()) {
        EmptyState(Icons.Outlined.FavoriteBorder, "Você ainda não salvou nenhum post.")
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Titulo("Salvos")
        Text("Você salvou ${salvos.size} post(s)", fontSize = 11.sp, color = TextDark)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(salvos, key = { it.id }) { post ->
                PostCard(post, estado.nomeDoAutor(post)) { onAbrirPost(post.id) }
            }
        }
    }
}

// ---------- 8. Novo post ----------
@Composable
fun NewPostScreen(estado: AppState, onPublicado: () -> Unit) {
    var titulo by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var imagem by remember { mutableStateOf<Uri?>(null) }

    val escolherImagem = rememberSeletorDeImagem { imagem = it }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Titulo("Novo post")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = titulo, onValueChange = { titulo = it },
            label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = descricao, onValueChange = { descricao = it },
            label = { Text("Descrição") }, minLines = 5, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        // Imagem é OPCIONAL
        Text("Imagem (opcional)", fontSize = 12.sp, color = TextDark)
        Spacer(Modifier.height(6.dp))
        if (imagem == null) {
            OutlinedButton(onClick = escolherImagem, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Image, null)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar imagem")
            }
        } else {
            AsyncImage(
                model = imagem, contentDescription = "Imagem do post",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp))
            )
            Row {
                TextButton(onClick = escolherImagem) { Text("Trocar imagem", color = Primary) }
                TextButton(onClick = { imagem = null }) { Text("Remover", color = Suave) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                estado.publicar(titulo.trim(), descricao.trim(), imagem)
                onPublicado()
            },
            enabled = titulo.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) { Text("Publicar") }
    }
}

// ---------- Telas adicionais integradas ----------
@Composable
private fun Campo(
    valor: String, aoMudar: (String) -> Unit, rotulo: String,
    teclado: KeyboardType = KeyboardType.Text, senha: Boolean = false
) {
    OutlinedTextField(
        value = valor, onValueChange = aoMudar, label = { Text(rotulo) }, singleLine = true,
        visualTransformation = if (senha) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (senha) KeyboardType.Password else teclado),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun BotaoPrincipal(texto: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
    ) { Text(texto) }
}

// ---------- Criar conta ----------
@Composable
fun SignupScreen(estado: AppState, onConcluido: () -> Unit) {
    var usuario by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Book, null, tint = Color.White)
        }
        Text("LÊA", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(Modifier.height(16.dp))
        Titulo("Crie uma conta!")
        Spacer(Modifier.height(16.dp))
        Campo(usuario, { usuario = it }, "Crie um nome de usuário")
        Campo(email, { email = it }, "Adicione um Email", KeyboardType.Email)
        Campo(senha, { senha = it }, "Crie uma senha", senha = true)
        Campo(telefone, { telefone = it }, "Telefone", KeyboardType.Phone)
        BotaoPrincipal("Criar conta", usuario.isNotBlank() && email.isNotBlank() && senha.isNotBlank()) {
            estado.nomeUsuario = usuario.trim()
            estado.email = email.trim()
            estado.telefone = telefone.trim()
            onConcluido()
        }
    }
}

// ---------- Recuperar senha: 1) e-mail/telefone ----------
@Composable
fun ForgotScreen(onTenhoCodigo: () -> Unit) {
    var contato by remember { mutableStateOf("") }
    var enviado by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Titulo("Esqueceu sua senha?")
        Text(
            "Para continuar, insira o endereço de e-mail ou número de telefone que você usa com sua conta.",
            color = TextDark, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(Modifier.height(8.dp))
        Campo(contato, { contato = it }, "E-mail ou número de telefone")
        if (enviado) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Verde).padding(10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Email de recuperação enviado com sucesso!", color = Color.White, fontSize = 12.sp) }
            Spacer(Modifier.height(10.dp))
        }
        // O envio é simulado por enquanto: precisa de um servidor para mandar o código de verdade
        BotaoPrincipal(if (enviado) "Reenviar Código" else "Enviar Código", contato.isNotBlank()) { enviado = true }
        if (enviado) TextButton(onClick = onTenhoCodigo, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Já recebi o código", color = Primary)
        }
    }
}

// ---------- Recuperar senha: 2) código de 4 dígitos ----------
@Composable
fun CodeScreen(onContinuar: () -> Unit) {
    val digitos = remember { mutableStateListOf("", "", "", "") }
    val foco = LocalFocusManager.current

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Titulo("Digite o código")
        Text("Digite o código que enviamos para você", color = TextDark, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            digitos.forEachIndexed { i, d ->
                OutlinedTextField(
                    value = d,
                    onValueChange = { v ->
                        val c = v.filter { it.isDigit() }.takeLast(1)
                        digitos[i] = c
                        if (c.isNotEmpty() && i < 3) foco.moveFocus(FocusDirection.Next)
                    },
                    singleLine = true,
                    textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 20.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(64.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        BotaoPrincipal("Continuar", digitos.all { it.isNotEmpty() }, onContinuar)
    }
}

// ---------- Recuperar senha: 3) nova senha com requisitos ----------
@Composable
fun NewPasswordScreen(onConcluido: () -> Unit) {
    var senha by remember { mutableStateOf("") }
    var confirma by remember { mutableStateOf("") }
    val regras = listOf(
        "A senha precisa ter no mínimo 8 caracteres" to (senha.length >= 8),
        "Utilize pelo menos uma letra minúscula" to senha.any { it.isLowerCase() },
        "Utilize pelo menos uma letra MAIÚSCULA" to senha.any { it.isUpperCase() },
        "Utilize pelo menos um número" to senha.any { it.isDigit() },
        "Utilize pelo menos um caractere especial como (@#\$%&*)" to senha.any { !it.isLetterOrDigit() },
    )

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Titulo("Redefinir senha")
        Spacer(Modifier.height(16.dp))
        Campo(senha, { senha = it }, "Digite sua nova senha", senha = true)
        regras.forEach { (texto, ok) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Default.Check else Icons.Default.Close, null,
                    tint = if (ok) Verde else Color.Gray, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(texto, fontSize = 12.sp, color = TextDark)
            }
        }
        Spacer(Modifier.height(16.dp))
        Campo(confirma, { confirma = it }, "Confirme a senha", senha = true)
        BotaoPrincipal("Continuar", regras.all { it.second } && senha == confirma, onConcluido)
    }
}

// ---------- Lista de conversas ----------
@Composable
fun ChatsListScreen(estado: AppState, onAbrir: () -> Unit) {
    if (estado.chats.isEmpty()) {
        EmptyState(Icons.Outlined.Email, "Nenhuma conversa ainda.\nAbra um grupo para começar a conversar.")
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Titulo("Chats")
        LazyColumn {
            items(estado.chats.keys.toList()) { nome ->
                Column(Modifier.fillMaxWidth().clickable { estado.abrirChat(nome); onAbrir() }.padding(vertical = 10.dp)) {
                    Text(nome, fontWeight = FontWeight.Bold, color = TextDark)
                    Text(estado.chats[nome]?.lastOrNull()?.texto ?: "Nenhuma mensagem ainda", fontSize = 11.sp, color = Suave)
                }
            }
        }
    }
}

// ---------- Notificações ----------
@Composable
fun NotificationsScreen(estado: AppState, onVoltar: () -> Unit) {
    var soMencoes by remember { mutableStateOf(false) }
    val lista = estado.notificacoes.filter { !soMencoes || it.mencao }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextDark) }
            Titulo("Notificações", Modifier.weight(1f))
            Icon(Icons.Default.Search, "Buscar", tint = TextDark)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !soMencoes, onClick = { soMencoes = false }, label = { Text("Todos") })
            FilterChip(selected = soMencoes, onClick = { soMencoes = true }, label = { Text("Menções") })
        }
        listOf("Importante", "Hoje", "Esta semana").forEach { secao ->
            Spacer(Modifier.height(16.dp))
            Text(secao, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Suave)
            val itens = lista.filter { it.secao == secao }
            if (itens.isEmpty()) {
                Text("Sem notificações", fontSize = 12.sp, color = TextDark, modifier = Modifier.padding(top = 6.dp))
            } else itens.forEach {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(it.texto, fontSize = 12.sp, color = TextDark)
                }
            }
        }
    }
}

// ---------- Perfil (próprio ou de outra pessoa) ----------
@Composable
fun PerfilScreen(
    nome: String, foto: Uri?, posts: List<Post>, ehProprio: Boolean,
    seguidores: Int = 0, seguindoQtd: Int = 0, seguindo: Boolean = false,
    onSeguir: () -> Unit = {},
    onNovoPost: () -> Unit = {}, onEditar: () -> Unit = {}, onMensagem: () -> Unit = {},
    onAbrirPost: (Int) -> Unit = {}
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ehProprio) IconButton(onClick = onNovoPost) { Icon(Icons.Default.Add, "Novo post", tint = TextDark) }
            else Spacer(Modifier.width(48.dp))
            Titulo("Perfil", Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally))
            Icon(Icons.Default.Search, "Buscar", tint = TextDark, modifier = Modifier.padding(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(72.dp, foto)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(nome, fontWeight = FontWeight.Bold, color = TextDark)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOf("Seguidores" to seguidores, "Seguindo" to seguindoQtd).forEach { (rotulo, qtd) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(qtd.toString(), fontSize = 12.sp, color = TextDark)
                            Text(rotulo, fontSize = 10.sp, color = Suave)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ehProprio) {
                OutlinedButton(onClick = onEditar, modifier = Modifier.weight(1f)) { Text("Editar") }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Compartilhar perfil", fontSize = 12.sp) }
            } else {
                OutlinedButton(onClick = onSeguir, modifier = Modifier.weight(1f)) {
                    Text(if (seguindo) "Seguindo" else "Seguir")
                }
                OutlinedButton(onClick = onMensagem, modifier = Modifier.weight(1f)) { Text("Mensagem") }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (posts.isEmpty()) {
            Box(Modifier.weight(1f)) { EmptyState(Icons.Outlined.Book, "Nenhum post publicado ainda.") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(posts, key = { it.id }) { p ->
                    Card(onClick = { onAbrirPost(p.id) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        if (p.imagem != null) {
                            AsyncImage(
                                model = p.imagem, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(110.dp)
                            )
                        }
                        Text(p.titulo, fontSize = 11.sp, color = TextDark, maxLines = 2, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}
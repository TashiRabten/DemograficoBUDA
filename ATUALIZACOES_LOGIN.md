# 🔐 Atualizações - Sistema de Login e Multi-Usuário

## ✨ Novas Funcionalidades Implementadas

### 1. Sistema de Autenticação Local

✅ **Login de usuários** sem senha (sistema simples e prático)
✅ **Registro de novos usuários** diretamente pelo app
✅ **Sessão persistente** - lembra do último usuário logado
✅ **Múltiplos usuários** podem usar o mesmo computador

### 2. Rastreamento de Dados por Usuário

✅ **Campo `usuario_coletor`** em todas as entrevistas
✅ **Identificação automática** do usuário que coletou cada dado
✅ **Sincronização** do usuário com Supabase

### 3. Nova Tabela do Supabase

✅ **Tabela atualizada**: `dados_inter_relacionais`
✅ **Projeto Supabase**: `cmfoaozuhvptlmojjqxx`
✅ **URL**: https://cmfoaozuhvptlmojjqxx.supabase.co
✅ **API Key atualizada** no código

---

## 📋 Campos/Variáveis Criados

### Novos Campos na Tabela `dados_inter_relacionais`

| Campo | Tipo | Descrição | Exemplo |
|-------|------|-----------|---------|
| `usuario_coletor` | VARCHAR(100) | Nome de usuário que fez a coleta | "joao_silva" |

### Banco de Dados Local

**Novo arquivo**: `demografico_users.db`

**Tabela**: `usuarios`

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | INTEGER | ID auto-incrementado |
| `nome_usuario` | TEXT | Username único (minúsculo) |
| `nome_completo` | TEXT | Nome completo do usuário |
| `email` | TEXT | Email (opcional) |
| `ultimo_login` | TEXT | Data/hora do último login |
| `created_at` | TEXT | Data de criação |

**Tabela atualizada**: `entrevistas`
- Adicionado campo: `usuario_coletor TEXT NOT NULL`

---

## 🆕 Novas Classes Java

### 1. `Usuario.java`
Modelo de dados para usuário logado:
- `nomeUsuario` - Nome de login
- `nomeCompleto` - Nome completo
- `email` - Email opcional
- `logado` - Status de login

### 2. `AuthManager.java`
Gerenciador de autenticação:
- `registrarUsuario()` - Cadastra novo usuário
- `login()` - Faz login
- `logout()` - Faz logout
- `getUsuarioAtual()` - Retorna usuário logado
- `isLogado()` - Verifica se há usuário logado
- `restaurarSessao()` - Restaura último login
- `usuarioExiste()` - Verifica se usuário existe

### 3. `LoginController.java`
Controller da tela de login:
- Gerencia tela de login
- Permite registrar novos usuários
- Abre janela principal após login
- Passa AuthManager para MainController

### 4. Interface FXML: `login-view.fxml`
Tela de login com:
- Campo de nome de usuário
- Campos para novo usuário (nome completo, email)
- Botões "Entrar" e "Novo Usuário"
- Mensagens de erro/sucesso
- Informação do último usuário logado

---

## 🔄 Classes Modificadas

### 1. `EntrevistadoDTO.java`
✅ Adicionado campo: `public String usuario_coletor`
✅ Construtor atualizado com parâmetro `usuarioColetor`

### 2. `SupabaseClient.java`
✅ URL atualizada para: `https://cmfoaozuhvptlmojjqxx.supabase.co`
✅ API Key atualizada
✅ Tabela alterada para: `dados_inter_relacionais`
✅ Métodos `inserirEntrevista()` e `atualizarEntrevista()` incluem `usuario_coletor`

### 3. `DatabaseManager.java`
✅ Tabela `entrevistas` com campo `usuario_coletor TEXT NOT NULL`
✅ Método `inserirEntrevista()` atualizado
✅ Métodos `buscarTodasEntrevistas()` e `buscarEntrevistasNaoSincronizadas()` incluem `usuario_coletor`

### 4. `MainController.java`
✅ Adicionado campo `private AuthManager authManager`
✅ Método `setAuthManager()` para receber instância
✅ Método `onSalvar()` obtém usuário logado e inclui em entrevistas
✅ Verificação de null para authManager

### 5. `DemograficoBUDAMain.java`
✅ Inicia com `login-view.fxml` em vez de `main-view.fxml`
✅ Abre janela de login primeiro
✅ Controller alterado de `MainController` para `LoginController`
✅ Janela principal é aberta após login bem-sucedido

---

## 🚀 Fluxo de Uso Atualizado

### 1️⃣ Primeiro Uso (Novo Usuário)

```
1. Executar programa
2. Tela de Login aparece
3. Clicar em "Novo Usuário"
4. Preencher:
   - Nome de usuário (ex: joao)
   - Nome completo (ex: João Silva)
   - Email (opcional)
5. Clicar em "Registrar"
6. Janela principal abre automaticamente
7. Começar a coletar dados
```

### 2️⃣ Usos Subsequentes (Usuário Existente)

```
1. Executar programa
2. Tela de Login mostra "Último usuário: joao"
3. Campo já vem preenchido
4. Clicar em "Entrar"
5. Janela principal abre
6. Continuar coletando dados
```

### 3️⃣ Trocar de Usuário

```
1. Na tela de login
2. Apagar nome de usuário
3. Digitar outro nome de usuário
4. Clicar em "Entrar"
```

---

## 📊 Estrutura de Dados no Supabase

### Tabela: `dados_inter_relacionais`

```sql
CREATE TABLE dados_inter_relacionais (
    id BIGSERIAL PRIMARY KEY,
    nome_entrevistado VARCHAR(255) NOT NULL,
    idade INTEGER NOT NULL CHECK (idade >= 0 AND idade <= 150),
    sexo VARCHAR(50) NOT NULL,
    tradicao VARCHAR(100) NOT NULL,
    templo VARCHAR(255),
    tempo_pratica INTEGER NOT NULL CHECK (tempo_pratica >= 0),
    mesclou_tradicoes BOOLEAN NOT NULL,
    observacoes TEXT,
    usuario_coletor VARCHAR(100) NOT NULL,  -- ✨ NOVO CAMPO
    data_entrevista TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Índice para buscar por usuário coletor
CREATE INDEX idx_usuario_coletor ON dados_inter_relacionais(usuario_coletor);
```

---

## 🔍 Queries Úteis

### Ver entrevistas por usuário:

```sql
SELECT
    usuario_coletor,
    COUNT(*) as total_entrevistas,
    ROUND(AVG(idade), 1) as idade_media
FROM dados_inter_relacionais
GROUP BY usuario_coletor
ORDER BY total_entrevistas DESC;
```

### Ver dados de um usuário específico:

```sql
SELECT *
FROM dados_inter_relacionais
WHERE usuario_coletor = 'joao'
ORDER BY created_at DESC;
```

### Estatísticas por usuário e tradição:

```sql
SELECT
    usuario_coletor,
    tradicao,
    COUNT(*) as total,
    SUM(CASE WHEN mesclou_tradicoes THEN 1 ELSE 0 END) as mesclou_count
FROM dados_inter_relacionais
GROUP BY usuario_coletor, tradicao
ORDER BY usuario_coletor, total DESC;
```

---

## 🎯 Benefícios das Mudanças

### ✅ Rastreabilidade
- Saber quem coletou cada entrevista
- Auditar dados por coletor
- Identificar padrões por região/coletor

### ✅ Multi-Usuário
- Vários coletores podem usar o mesmo computador
- Cada um tem seus dados separados localmente
- Todos compartilham dados na nuvem

### ✅ Segurança Simples
- Login sem senha (simples para uso interno)
- Sessão persistente (não precisa logar sempre)
- Perfis separados

### ✅ Análise de Dados
- Comparar coletores
- Ver produtividade por pessoa
- Identificar viés de coleta

---

## 🗂️ Arquivos do Projeto (Atualizados)

```
DemograficoBUDA/
├── src/main/java/com/buda/demografico/
│   ├── DemograficoBUDAMain.java       # ✏️ Modificado - inicia com login
│   ├── LoginController.java            # ✨ NOVO - gerencia login
│   ├── MainController.java             # ✏️ Modificado - recebe AuthManager
│   ├── Usuario.java                    # ✨ NOVO - modelo de usuário
│   ├── AuthManager.java                # ✨ NOVO - autenticação
│   ├── EntrevistadoDTO.java            # ✏️ Modificado - campo usuario_coletor
│   ├── SupabaseClient.java             # ✏️ Modificado - nova tabela/API
│   └── DatabaseManager.java            # ✏️ Modificado - campo usuario_coletor
├── src/main/resources/com/buda/demografico/
│   ├── login-view.fxml                 # ✨ NOVO - tela de login
│   ├── main-view.fxml                  # (sem alterações)
│   └── styles.css                      # (sem alterações)
└── pom.xml                             # (sem alterações)
```

---

## ⚙️ Configuração Necessária no Supabase

### 1. Adicionar coluna na tabela existente:

```sql
-- Adicionar campo usuario_coletor
ALTER TABLE dados_inter_relacionais
ADD COLUMN usuario_coletor VARCHAR(100);

-- Atualizar registros existentes (se houver)
UPDATE dados_inter_relacionais
SET usuario_coletor = 'anonimo'
WHERE usuario_coletor IS NULL;

-- Tornar campo obrigatório
ALTER TABLE dados_inter_relacionais
ALTER COLUMN usuario_coletor SET NOT NULL;

-- Adicionar índice
CREATE INDEX idx_usuario_coletor ON dados_inter_relacionais(usuario_coletor);
```

### 2. Verificar permissões RLS:

```sql
-- Permitir INSERT com usuario_coletor
CREATE POLICY "Permitir inserção com usuário"
ON dados_inter_relacionais
FOR INSERT
WITH CHECK (usuario_coletor IS NOT NULL);
```

---

## ✅ Status da Compilação

```
[INFO] BUILD SUCCESS
[INFO] Compiling 8 source files ✅
[INFO] Total time: 6.446 s
```

**Projeto compilado com sucesso!** ✅

---

## 🚦 Próximos Passos

1. ✅ **Executar o programa**:
   ```bash
   mvn javafx:run
   ```

2. ✅ **Criar primeiro usuário** na tela de login

3. ✅ **Fazer entrevista de teste**

4. ✅ **Verificar no Supabase** se campo `usuario_coletor` está preenchido

5. ✅ **Testar com múltiplos usuários**

---

## 📞 Resumo das Credenciais

**Supabase:**
- URL: `https://cmfoaozuhvptlmojjqxx.supabase.co`
- Tabela: `dados_inter_relacionais`
- Dashboard: https://supabase.com/dashboard/project/cmfoaozuhvptlmojjqxx

**Banco Local:**
- Usuários: `demografico_users.db`
- Entrevistas: `demografico_buda.db`

---

**Sistema de login multi-usuário implementado com sucesso!** 🎉

*Associação BUDA - 2025*

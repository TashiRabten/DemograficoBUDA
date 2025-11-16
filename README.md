# DemograficoBUDA - Programa de Coleta de Dados

![Java](https://img.shields.io/badge/Java-17-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-17-blue)
![Supabase](https://img.shields.io/badge/Supabase-Cloud-green)

Programa de coleta de dados demográficos sobre práticas budistas no Brasil, desenvolvido para a **Associação BUDA**.

## 📋 Sobre o Projeto

Este programa foi desenvolvido para auxiliar na coleta de dados para uma pesquisa sobre a **interrelação de tradições budistas**. O objetivo é verificar se praticantes budistas mesclaram práticas do budismo originário com outras tradições (Tibetana, Zen, Theravada, etc.).

Os dados coletados serão utilizados para:
- Análises estatísticas
- Publicação em revista acadêmica sobre a rede inter-relacional do budismo
- Compreensão do panorama budista brasileiro

## ✨ Funcionalidades

- ✅ **Interface JavaFX amigável** inspirada no GlossarioBUDA Compacto
- ✅ **Armazenamento local** em SQLite (funciona offline)
- ✅ **Sincronização com Supabase** (base de dados centralizada na nuvem)
- ✅ **Coleta de dados demográficos:**
  - Nome do entrevistado
  - Idade
  - Sexo/Gênero
  - Tradição budista
  - Templo/Centro de prática
  - Tempo de prática (anos)
  - Mesclou tradições? (Sim/Não)
  - Observações adicionais
- ✅ **Estatísticas em tempo real**
- ✅ **Exportação de dados** para análise estatística

## 🛠️ Tecnologias Utilizadas

- **Java 17**
- **JavaFX 17** (Interface gráfica)
- **SQLite** (Banco de dados local)
- **Supabase** (Banco de dados na nuvem)
- **Gson** (Processamento JSON)
- **Maven** (Gerenciamento de dependências)

## 📦 Pré-requisitos

- **Java JDK 17** ou superior
- **Maven 3.6** ou superior
- Conexão com internet (para sincronização com Supabase)

## 🚀 Como Executar

### 1. Compilar o Projeto

```bash
cd /mnt/c/Users/tashi.TASHI-LENOVO/APPS/DemograficoBUDA
mvn clean compile
```

### 2. Executar via Maven

```bash
mvn javafx:run
```

### 3. Gerar JAR Executável

```bash
mvn clean package
```

O arquivo JAR será gerado em: `target/DemograficoBUDA-1.0-SNAPSHOT.jar`

Para executar o JAR:

```bash
java -jar target/DemograficoBUDA-1.0-SNAPSHOT.jar
```

## 🗄️ Configuração do Supabase

### Criar a Tabela no Supabase

1. Acesse o dashboard do Supabase: https://app.supabase.com
2. Selecione o projeto `ctbufeavhuypjdbqmwzb`
3. Vá em **SQL Editor**
4. Execute o script SQL disponível em `SUPABASE_SETUP.md`

A tabela `pesquisa_demografica_budista` será criada com os seguintes campos:

| Campo | Tipo | Descrição |
|-------|------|-----------|
| id | BIGSERIAL | ID único (auto-incrementado) |
| nome_entrevistado | VARCHAR(255) | Nome completo |
| idade | INTEGER | Idade (0-150) |
| sexo | VARCHAR(50) | Sexo/Gênero |
| tradicao | VARCHAR(100) | Tradição budista |
| templo | VARCHAR(255) | Templo ou centro |
| tempo_pratica | INTEGER | Anos de prática |
| mesclou_tradicoes | BOOLEAN | Mesclou tradições |
| observacoes | TEXT | Observações |
| data_entrevista | TIMESTAMP | Data da entrevista |
| created_at | TIMESTAMP | Data de criação |
| updated_at | TIMESTAMP | Última atualização |

### Credenciais Supabase

As credenciais já estão configuradas no código:

- **URL**: `https://ctbufeavhuypjdbqmwzb.supabase.co`
- **API Key**: Configurada em `SupabaseClient.java`

## 📊 Como Usar o Programa

### 1. Iniciar o Programa

Execute o programa seguindo as instruções acima. A interface será aberta.

### 2. Testar Conexão

Clique no botão **"Testar Conexão"** para verificar se o programa está conectado ao Supabase.

- ✅ **Verde (Conectado)**: Dados serão salvos localmente E na nuvem
- 🟠 **Laranja (Offline)**: Dados serão salvos apenas localmente

### 3. Preencher Formulário

Preencha os campos obrigatórios:

- Nome Completo
- Idade
- Sexo/Gênero (selecione do dropdown)
- Tradição Budista (selecione do dropdown)
- Templo/Centro (opcional)
- Tempo de Prática em anos
- Mesclou Tradições? (Sim/Não)
- Observações (opcional)

### 4. Salvar Entrevista

Clique em **"Salvar Entrevista"**:

- Se **online**: salva localmente e sincroniza automaticamente
- Se **offline**: salva localmente para sincronizar depois

### 5. Sincronizar Dados

Se você coletou dados offline, clique em **"Sincronizar"** quando tiver conexão para enviar ao Supabase.

### 6. Visualizar Estatísticas

O programa mostra em tempo real:

- **Entrevistas Locais**: Total no banco local
- **Não Sincronizadas**: Quantas ainda precisam ser enviadas
- **Total na Nuvem**: Total no Supabase (compartilhado)

Clique em **"Atualizar"** para refresh dos dados.

## 📈 Exportar Dados para Análise

### Via Supabase Dashboard

1. Acesse https://app.supabase.com
2. Vá em **Table Editor** > `pesquisa_demografica_budista`
3. Clique em **Export** > **CSV**

### Via SQL (Estatísticas)

Execute no SQL Editor do Supabase:

```sql
-- Estatísticas por tradição
SELECT
    tradicao,
    COUNT(*) as total_praticantes,
    ROUND(AVG(idade), 1) as idade_media,
    ROUND(AVG(tempo_pratica), 1) as tempo_pratica_medio,
    SUM(CASE WHEN mesclou_tradicoes THEN 1 ELSE 0 END) as mesclou_count,
    ROUND(100.0 * SUM(CASE WHEN mesclou_tradicoes THEN 1 ELSE 0 END) / COUNT(*), 1) as percentual_mesclou
FROM pesquisa_demografica_budista
GROUP BY tradicao
ORDER BY total_praticantes DESC;

-- Distribuição por sexo
SELECT
    sexo,
    COUNT(*) as total,
    ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM pesquisa_demografica_budista), 1) as percentual
FROM pesquisa_demografica_budista
GROUP BY sexo
ORDER BY total DESC;

-- Ver todos os dados
SELECT * FROM pesquisa_demografica_budista
ORDER BY data_entrevista DESC;
```

## 📁 Estrutura do Projeto

```
DemograficoBUDA/
├── src/
│   ├── main/
│   │   ├── java/com/buda/demografico/
│   │   │   ├── DemograficoBUDAMain.java      # Classe principal
│   │   │   ├── MainController.java            # Controller JavaFX
│   │   │   ├── EntrevistadoDTO.java           # Modelo de dados
│   │   │   ├── SupabaseClient.java            # Cliente Supabase
│   │   │   └── DatabaseManager.java           # Banco SQLite local
│   │   └── resources/com/buda/demografico/
│   │       ├── main-view.fxml                 # Interface FXML
│   │       └── styles.css                     # Estilos CSS
├── pom.xml                                     # Configuração Maven
├── README.md                                   # Este arquivo
└── SUPABASE_SETUP.md                          # Instruções Supabase
```

## 🔧 Desenvolvimento

### Adicionar Novas Funcionalidades

O projeto está estruturado de forma modular:

- **Interface**: Edite `main-view.fxml` e `MainController.java`
- **Banco Local**: Modifique `DatabaseManager.java`
- **Supabase**: Ajuste `SupabaseClient.java`
- **Modelo de Dados**: Atualize `EntrevistadoDTO.java`

### Compilar para Distribuição

```bash
mvn clean package
```

O JAR estará em `target/DemograficoBUDA-1.0-SNAPSHOT.jar` e pode ser distribuído para outros usuários.

## 🐛 Solução de Problemas

### Erro ao conectar ao Supabase

- Verifique sua conexão com internet
- Confirme que as credenciais estão corretas
- O programa funcionará offline e sincronizará depois

### Erro ao iniciar JavaFX

- Certifique-se de ter Java 17 ou superior instalado
- Execute via Maven: `mvn javafx:run`

### Banco de dados não salva

- Verifique permissões de escrita na pasta do programa
- O arquivo `demografico_buda.db` será criado automaticamente

## 📝 Licença

Este projeto foi desenvolvido para a **Associação BUDA** para fins de pesquisa acadêmica.

## 👥 Contato

Para dúvidas ou sugestões, entre em contato com a Associação BUDA.

---

**Desenvolvido com ❤️ para a comunidade budista brasileira**

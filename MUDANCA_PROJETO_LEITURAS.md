# 📚 Mudança de Projeto - Pesquisa sobre Leituras Budistas

## 🔄 Mudança Principal

**ANTES**: Pesquisa sobre mesclagem de tradições budistas
**AGORA**: Pesquisa sobre **o que praticantes budistas leem**

---

## 📊 Nova Estrutura de Dados

### Tabela Supabase: `leituras_budistas`

```sql
CREATE TABLE leituras_budistas (
    id BIGSERIAL PRIMARY KEY,

    -- Dados demográficos (mantidos)
    nome_entrevistado VARCHAR(255) NOT NULL,
    idade INTEGER NOT NULL CHECK (idade >= 0 AND idade <= 150),
    sexo VARCHAR(50) NOT NULL,
    tradicao VARCHAR(100) NOT NULL,
    templo VARCHAR(255),
    tempo_pratica INTEGER NOT NULL CHECK (tempo_pratica >= 0),

    -- NOVOS CAMPOS - Dados sobre leitura
    tipo_leitura VARCHAR(100) NOT NULL,       -- Livros, Revistas, Tratados, Sutras, etc.
    titulo_obra VARCHAR(500),                 -- Título do que está lendo
    autor VARCHAR(255),                       -- Autor da obra
    idioma_leitura VARCHAR(50),               -- Português, Tibetano, Inglês, etc.
    frequencia_leitura VARCHAR(50),           -- Diária, Semanal, Mensal, etc.

    -- Campos de controle
    observacoes TEXT,
    usuario_coletor VARCHAR(100) NOT NULL,
    data_entrevista TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Índices
CREATE INDEX idx_tipo_leitura ON leituras_budistas(tipo_leitura);
CREATE INDEX idx_usuario_coletor ON leituras_budistas(usuario_coletor);
CREATE INDEX idx_tradicao ON leituras_budistas(tradicao);
CREATE INDEX idx_idioma ON leituras_budistas(idioma_leitura);

-- Trigger para updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_leituras_budistas_updated_at
    BEFORE UPDATE ON leituras_budistas
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

---

## 🆕 Novos Campos da Pesquisa

| Campo | Tipo | Descrição | Exemplos |
|-------|------|-----------|----------|
| `tipo_leitura` | VARCHAR(100) | Tipo de material | "Livros", "Revistas", "Tratados", "Sutras", "Artigos" |
| `titulo_obra` | VARCHAR(500) | Título da obra | "O Caminho do Bodhisattva", "Palavras de Meu Mestre Perfeito" |
| `autor` | VARCHAR(255) | Autor | "Shantideva", "Patrul Rinpoche", "Thich Nhat Hanh" |
| `idioma_leitura` | VARCHAR(50) | Idioma | "Português", "Inglês", "Tibetano", "Chinês" |
| `frequencia_leitura` | VARCHAR(50) | Frequência | "Diária", "Semanal", "Mensal", "Esporádica" |

### Campos Removidos:
- ❌ `mesclou_tradicoes` (BOOLEAN) - Não é mais relevante

---

## 📝 Opções Pré-definidas

### Tipo de Leitura:
- Livros (Dharma)
- Revistas Budistas
- Tratados/Comentários
- Sutras
- Artigos Online
- Textos de Meditação
- Biografias de Mestres
- Outro

### Idioma de Leitura:
- Português
- Inglês
- Tibetano
- Chinês
- Sânscrito
- Pali
- Outro

### Frequência de Leitura:
- Diária
- Semanal (2-3x por semana)
- Semanal (1x por semana)
- Quinzenal
- Mensal
- Esporádica
- Raramente

---

## 🔄 Mudanças nas Classes Java

### 1. Novo DTO: `LeituraDTO.java`

Substitui `EntrevistadoDTO.java`

**Campos principais:**
```java
public class LeituraDTO {
    // Dados demográficos
    public String nome_entrevistado;
    public Integer idade;
    public String sexo;
    public String tradicao;
    public String templo;
    public Integer tempo_pratica;

    // Dados sobre leitura (NOVO)
    public String tipo_leitura;
    public String titulo_obra;
    public String autor;
    public String idioma_leitura;
    public String frequencia_leitura;

    // Controle
    public String observacoes;
    public String usuario_coletor;
}
```

### 2. SupabaseClient.java - Métodos Atualizados

| Método Antigo | Método Novo |
|---------------|-------------|
| `inserirEntrevista()` | `inserirLeitura()` |
| `buscarTodasEntrevistas()` | `buscarTodasLeituras()` |
| `contarEntrevistas()` | `contarLeituras()` |
| `buscarEstatisticasPorTradicao()` | `buscarEstatisticasPorTipo()` |
| `atualizarEntrevista()` | `atualizarLeitura()` |

**Tabela atualizada:**
- De: `dados_inter_relacionais`
- Para: `leituras_budistas`

### 3. DatabaseManager.java - Banco Local Atualizado

**Arquivo de banco:**
- De: `demografico_buda.db`
- Para: `leituras_buda.db`

**Tabela:**
- De: `entrevistas`
- Para: `leituras`

**Novos campos:**
```sql
tipo_leitura TEXT NOT NULL,
titulo_obra TEXT,
autor TEXT,
idioma_leitura TEXT,
frequencia_leitura TEXT
```

---

## 🎯 Nova Interface do Usuário

### Campos do Formulário:

**Seção 1: Dados do Entrevistado** (mantidos)
- Nome Completo
- Idade
- Sexo/Gênero
- Tradição Budista
- Templo/Centro
- Tempo de Prática (anos)

**Seção 2: Dados de Leitura** (NOVA)
- **Tipo de Leitura** (ComboBox)
  - Livros, Revistas, Tratados, Sutras, etc.

- **Título da Obra** (TextField)
  - Ex: "O Caminho do Bodhisattva"

- **Autor** (TextField)
  - Ex: "Shantideva"

- **Idioma de Leitura** (ComboBox)
  - Português, Inglês, Tibetano, etc.

- **Frequência de Leitura** (ComboBox)
  - Diária, Semanal, Mensal, etc.

- **Observações** (TextArea)
  - Campo livre

---

## 📊 Queries de Análise

### 1. Estatísticas por Tipo de Leitura

```sql
SELECT
    tipo_leitura,
    COUNT(*) as total_leitores,
    ROUND(AVG(idade), 1) as idade_media,
    ROUND(AVG(tempo_pratica), 1) as tempo_pratica_medio
FROM leituras_budistas
GROUP BY tipo_leitura
ORDER BY total_leitores DESC;
```

### 2. Obras Mais Lidas

```sql
SELECT
    titulo_obra,
    autor,
    COUNT(*) as vezes_mencionada,
    STRING_AGG(DISTINCT idioma_leitura, ', ') as idiomas
FROM leituras_budistas
WHERE titulo_obra IS NOT NULL
GROUP BY titulo_obra, autor
ORDER BY vezes_mencionada DESC
LIMIT 20;
```

### 3. Distribuição por Idioma

```sql
SELECT
    idioma_leitura,
    COUNT(*) as total,
    ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM leituras_budistas), 1) as percentual
FROM leituras_budistas
WHERE idioma_leitura IS NOT NULL
GROUP BY idioma_leitura
ORDER BY total DESC;
```

### 4. Frequência de Leitura por Tradição

```sql
SELECT
    tradicao,
    frequencia_leitura,
    COUNT(*) as total
FROM leituras_budistas
GROUP BY tradicao, frequencia_leitura
ORDER BY tradicao, total DESC;
```

### 5. Autores Mais Lidos

```sql
SELECT
    autor,
    COUNT(*) as total_leitores,
    STRING_AGG(DISTINCT titulo_obra, ' | ') as obras_mencionadas
FROM leituras_budistas
WHERE autor IS NOT NULL
GROUP BY autor
ORDER BY total_leitores DESC
LIMIT 20;
```

---

## 🔧 Passos para Atualizar no Supabase

### Opção 1: Criar Nova Tabela (Recomendado)

```sql
-- 1. Criar nova tabela leituras_budistas
-- (usar SQL completo acima)

-- 2. Migrar dados demográficos (se necessário)
INSERT INTO leituras_budistas (
    nome_entrevistado, idade, sexo, tradicao,
    templo, tempo_pratica, usuario_coletor,
    tipo_leitura, observacoes
)
SELECT
    nome_entrevistado, idade, sexo, tradicao,
    templo, tempo_pratica, usuario_coletor,
    'Não especificado' as tipo_leitura,
    observacoes
FROM dados_inter_relacionais;

-- 3. Configurar RLS (Row Level Security)
ALTER TABLE leituras_budistas ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Permitir leitura para todos"
ON leituras_budistas FOR SELECT USING (true);

CREATE POLICY "Permitir inserção para todos"
ON leituras_budistas FOR INSERT WITH CHECK (true);

CREATE POLICY "Permitir atualização para todos"
ON leituras_budistas FOR UPDATE USING (true);
```

### Opção 2: Modificar Tabela Existente (Arriscado)

```sql
-- Adicionar novos campos
ALTER TABLE dados_inter_relacionais
ADD COLUMN tipo_leitura VARCHAR(100),
ADD COLUMN titulo_obra VARCHAR(500),
ADD COLUMN autor VARCHAR(255),
ADD COLUMN idioma_leitura VARCHAR(50),
ADD COLUMN frequencia_leitura VARCHAR(50);

-- Remover campo antigo
ALTER TABLE dados_inter_relacionais
DROP COLUMN mesclou_tradicoes;

-- Renomear tabela
ALTER TABLE dados_inter_relacionais
RENAME TO leituras_budistas;

-- Tornar tipo_leitura obrigatório
UPDATE leituras_budistas
SET tipo_leitura = 'Não especificado'
WHERE tipo_leitura IS NULL;

ALTER TABLE leituras_budistas
ALTER COLUMN tipo_leitura SET NOT NULL;
```

---

## 📈 Análises Possíveis com os Novos Dados

### 1. Padrões de Leitura
- Quais tipos de material são mais lidos?
- Qual a frequência média de leitura?
- Há diferença por tradição?

### 2. Preferências Linguísticas
- Que idiomas praticantes preferem?
- Correlação entre tradição e idioma?
- Acesso a textos em línguas originais?

### 3. Obras e Autores Populares
- Quais livros são mais lidos?
- Quais autores são mais influentes?
- Há diferença por idade/tempo de prática?

### 4. Diversidade de Leitura
- Praticantes leem múltiplos tipos?
- Correlação entre frequência e tipos?

---

## ✅ Checklist de Atualização

- [✅] Criar `LeituraDTO.java`
- [✅] Atualizar `SupabaseClient.java`
- [✅] Atualizar `DatabaseManager.java`
- [⏳] Atualizar `MainController.java`
- [⏳] Atualizar `main-view.fxml`
- [⏳] Compilar e testar
- [⏳] Criar tabela no Supabase
- [⏳] Teste completo do fluxo

---

## 🎯 Objetivo da Nova Pesquisa

**Meta**: Entender **o que praticantes budistas leem** para:

1. Identificar obras mais influentes
2. Mapear acesso a textos tradicionais
3. Entender barreiras linguísticas
4. Avaliar diversidade de leitura
5. Correlacionar leitura com engajamento (tempo de prática)
6. Mapear autores mais estudados no Brasil

---

**Projeto atualizado para foco em leituras budistas!** 📚

*Associação BUDA - 2025*

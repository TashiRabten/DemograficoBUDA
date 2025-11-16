# Configuração do Supabase para DemograficoBUDA

## Informações de Conexão

**URL do Supabase**: `https://ctbufeavhuypjdbqmwzb.supabase.co`
**API Key**: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN0YnVmZWF2aHV5cGpkYnFtd3piIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAyODQ2MzYsImV4cCI6MjA3NTg2MDYzNn0.lkaQWS8u2QisnRENtOXKDAMbPwhRV1jS4euBDc1TUDg`

## Estrutura da Tabela

### Tabela: `pesquisa_demografica_budista`

```sql
CREATE TABLE pesquisa_demografica_budista (
    id BIGSERIAL PRIMARY KEY,
    nome_entrevistado VARCHAR(255) NOT NULL,
    idade INTEGER NOT NULL CHECK (idade >= 0 AND idade <= 150),
    sexo VARCHAR(50) NOT NULL,
    tradicao VARCHAR(100) NOT NULL,
    templo VARCHAR(255),
    tempo_pratica INTEGER NOT NULL CHECK (tempo_pratica >= 0),
    mesclou_tradicoes BOOLEAN NOT NULL,
    observacoes TEXT,
    data_entrevista TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Índices para melhorar performance
CREATE INDEX idx_tradicao ON pesquisa_demografica_budista(tradicao);
CREATE INDEX idx_data_entrevista ON pesquisa_demografica_budista(data_entrevista);
CREATE INDEX idx_mesclou_tradicoes ON pesquisa_demografica_budista(mesclou_tradicoes);

-- Trigger para atualizar updated_at automaticamente
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_pesquisa_demografica_budista_updated_at
    BEFORE UPDATE ON pesquisa_demografica_budista
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

## Campos da Pesquisa

| Campo | Tipo | Descrição | Obrigatório |
|-------|------|-----------|-------------|
| `id` | BIGSERIAL | ID único auto-incrementado | Sim (auto) |
| `nome_entrevistado` | VARCHAR(255) | Nome completo do entrevistado | Sim |
| `idade` | INTEGER | Idade do entrevistado (0-150) | Sim |
| `sexo` | VARCHAR(50) | Sexo/Gênero do entrevistado | Sim |
| `tradicao` | VARCHAR(100) | Tradição budista (Tibetana, Zen, Theravada, etc.) | Sim |
| `templo` | VARCHAR(255) | Nome do templo ou centro de prática | Não |
| `tempo_pratica` | INTEGER | Tempo de prática em anos | Sim |
| `mesclou_tradicoes` | BOOLEAN | Se mesclou tradições (true/false) | Sim |
| `observacoes` | TEXT | Observações adicionais sobre a entrevista | Não |
| `data_entrevista` | TIMESTAMP | Data e hora da entrevista | Sim (auto) |
| `created_at` | TIMESTAMP | Data de criação do registro | Sim (auto) |
| `updated_at` | TIMESTAMP | Data da última atualização | Sim (auto) |

## Opções Pré-definidas para Campos

### Sexo/Gênero
- Masculino
- Feminino
- Não-binário
- Prefiro não informar
- Outro

### Tradições Budistas
- Tibetana (Vajrayana)
- Zen
- Theravada
- Terra Pura
- Nichiren
- Budismo Engajado
- Outro

## Passos para Criar a Tabela no Supabase

1. Acesse o dashboard do Supabase: https://app.supabase.com
2. Selecione o projeto `ctbufeavhuypjdbqmwzb`
3. Vá em "SQL Editor"
4. Cole o script SQL acima
5. Execute o script

## Permissões (RLS - Row Level Security)

Para permitir que a aplicação acesse os dados, configure as políticas:

```sql
-- Habilitar RLS
ALTER TABLE pesquisa_demografica_budista ENABLE ROW LEVEL SECURITY;

-- Permitir SELECT para todos
CREATE POLICY "Permitir leitura para todos"
ON pesquisa_demografica_budista
FOR SELECT
USING (true);

-- Permitir INSERT para todos
CREATE POLICY "Permitir inserção para todos"
ON pesquisa_demografica_budista
FOR INSERT
WITH CHECK (true);

-- Permitir UPDATE para todos
CREATE POLICY "Permitir atualização para todos"
ON pesquisa_demografica_budista
FOR UPDATE
USING (true);
```

## Exportação de Dados para Análise

Para exportar os dados coletados:

```sql
-- Ver todas as respostas
SELECT * FROM pesquisa_demografica_budista
ORDER BY data_entrevista DESC;

-- Estatísticas por tradição
SELECT
    tradicao,
    COUNT(*) as total_praticantes,
    ROUND(AVG(idade), 1) as idade_media,
    ROUND(AVG(tempo_pratica), 1) as tempo_pratica_medio,
    SUM(CASE WHEN mesclou_tradicoes THEN 1 ELSE 0 END) as mesclou_tradicoes_count,
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
```

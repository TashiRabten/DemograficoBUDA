# 📘 Guia Rápido - DemograficoBUDA

## ⚡ Início Rápido

### Windows

1. **Duplo-clique** em `run.bat`
2. Aguarde o programa abrir
3. Pronto! Comece a coletar dados

### Linha de Comando

```bash
cd DemograficoBUDA
mvn javafx:run
```

---

## 🎯 Como Usar em 5 Passos

### 1️⃣ Teste a Conexão

Ao abrir o programa, clique em **"Testar Conexão"**

- 🟢 **Verde** = Online (dados vão para a nuvem automaticamente)
- 🟠 **Laranja** = Offline (dados salvos localmente, sincronize depois)

### 2️⃣ Preencha o Formulário

**Campos Obrigatórios:**
- ✅ Nome Completo
- ✅ Idade
- ✅ Sexo/Gênero
- ✅ Tradição Budista
- ✅ Tempo de Prática (anos)
- ✅ Mesclou Tradições? (Sim/Não)

**Campos Opcionais:**
- Templo/Centro
- Observações

### 3️⃣ Salvar

Clique em **"Salvar Entrevista"**

- ✅ Sucesso = Dados salvos
- Se online, já está na nuvem!
- Se offline, será marcado para sincronização

### 4️⃣ Sincronizar (se estiver offline)

Quando tiver internet, clique em **"Sincronizar"**

- Todos os dados locais serão enviados ao Supabase
- Você verá quantas entrevistas foram sincronizadas

### 5️⃣ Acompanhe as Estatísticas

**Painel de Estatísticas mostra:**
- 📊 Entrevistas Locais
- 🔄 Não Sincronizadas
- ☁️ Total na Nuvem

Clique em **"Atualizar"** para refresh

---

## 💡 Dicas Importantes

### ✨ Boas Práticas

1. **Sempre teste a conexão antes de começar**
   - Garante que os dados vão direto para a nuvem

2. **Preencha observações relevantes**
   - Informações qualitativas enriquecem a pesquisa

3. **Sincronize regularmente**
   - Não deixe muitas entrevistas sem sincronizar

4. **Faça backup**
   - O arquivo `demografico_buda.db` contém todos os dados locais

### ⚠️ O Que Evitar

- ❌ Não feche o programa durante sincronização
- ❌ Não delete o arquivo `.db` (é seu backup local)
- ❌ Não insira dados falsos (prejudica a pesquisa)

---

## 🔍 Entendendo os Campos

### Tradições Budistas Disponíveis

| Tradição | Descrição |
|----------|-----------|
| **Tibetana (Vajrayana)** | Budismo tibetano, lamas, mantras |
| **Zen** | Meditação zazen, koans |
| **Theravada** | Vipassana, tradição dos anciãos |
| **Terra Pura** | Devoção ao Buda Amitabha |
| **Nichiren** | Baseado nos ensinamentos de Nichiren |
| **Budismo Engajado** | Foco em ação social |
| **Outro** | Outras tradições ou mistas |

### O Que Significa "Mesclou Tradições"?

**SIM** - Exemplos:
- Pratica meditação Zen + mantras tibetanos
- Frequenta templo Theravada + centro Zen
- Combina práticas de diferentes escolas

**NÃO** - Exemplos:
- Pratica apenas uma tradição
- Nunca misturou diferentes abordagens
- Mantém-se fiel a uma única escola

---

## 📊 Exportar Dados para Análise

### Via Supabase (Recomendado)

1. Acesse: https://app.supabase.com
2. Login no projeto `ctbufeavhuypjdbqmwzb`
3. Vá em **Table Editor** > `pesquisa_demografica_budista`
4. Clique em **Export** > **CSV**
5. Abra no Excel, SPSS, R, ou Python para análise

### Campos Exportados

- Nome, Idade, Sexo, Tradição
- Templo, Tempo de Prática
- Mesclou Tradições (true/false)
- Observações
- Data da Entrevista

---

## 🆘 Resolução de Problemas

### Problema: Não consegue salvar

**Solução:**
1. Verifique se preencheu todos os campos obrigatórios
2. Idade e Tempo de Prática devem ser números
3. Idade deve estar entre 0 e 150

### Problema: Não sincroniza

**Solução:**
1. Clique em "Testar Conexão"
2. Verifique sua internet
3. Se estiver conectado, clique em "Sincronizar"

### Problema: Programa não abre

**Solução:**
1. Verifique se tem Java 17 instalado
2. Execute via linha de comando: `mvn javafx:run`
3. Veja os erros no console

### Problema: Perdeu os dados

**Solução:**
1. Os dados estão em `demografico_buda.db`
2. Não delete este arquivo!
3. Faça backup regularmente

---

## 📞 Suporte

**Dúvidas sobre:**
- **Uso do programa**: Consulte este guia ou README.md
- **Pesquisa**: Entre em contato com a Associação BUDA
- **Problemas técnicos**: Verifique README.md seção "Solução de Problemas"

---

## 🎓 Para os Pesquisadores

### Análise Estatística Recomendada

**Estatísticas Descritivas:**
- Distribuição por tradição
- Média de idade por tradição
- Tempo médio de prática
- Percentual que mesclou tradições

**Análises Cruzadas:**
- Mesclagem x Tempo de Prática
- Mesclagem x Idade
- Mesclagem x Tradição Original

**Testes Estatísticos:**
- Chi-quadrado (mesclagem x tradição)
- Correlação (tempo de prática x mesclagem)
- ANOVA (idade entre diferentes tradições)

### Ferramentas Recomendadas

- **Excel**: Análise básica e gráficos
- **SPSS**: Análise estatística completa
- **R/Python**: Análise avançada e visualizações
- **Tableau**: Dashboards interativos

---

**Boa coleta de dados! 🙏**

*Desenvolvido para a Associação BUDA*

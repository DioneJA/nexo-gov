import csv, re, unicodedata
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/'docs/licitacoes/mata-de-sao-joao-91-2025'
text=(base/'edital-original/texto-extraido/EDITALPE91202501092025ass.txt').read_text(encoding='utf-8')
# The scanned Termo de Referência is indexed separately by extend-matrix-from-tr-ocr.py.

tr_map=[
 ('CARACTERISTICAS TECNICAS','4','3'),('CARACTERÍSTICAS TÉCNICAS','4','3'),('VISÃO GERAL','5','4'),('VISAO GERAL','5','4'),('ESTRUTURAÇÃO DAS INFORMAÇÕES','6','9'),('ESTRUTURACAO DAS INFORMACOES','6','9'),('DIVISÃO POR MÓDULO','7','10'),('DIVISAO POR MODULO','7','10'),('ORGANOGRAMAS','8','11'),('CALENDÁRIO E CONTROLE','9','11'),('CALENDARIO E CONTROLE','9','11'),('SITUAÇÃO DOS DOCUMENTOS','10','12'),('SITUACAO DOS DOCUMENTOS','10','12'),('PÁGINA DE PLANO','11','12'),('PAGINA DE PLANO','11','12'),('GERENCIAMENTO DE CONTATOS','12','13'),('DEMANDAS DO SETOR','13','13'),('FUNCIONALIDADE DE QUEM VISUALIZOU','14','13'),('STATUS DE LEITURA','15','13'),('CENTRAL DE ATENDIMENTO','16','14'),('NOTIFICAÇÃO MULTICANAIS','17','16'),('NOTIFICACAO MULTICANAIS','17','16'),('HOSPEDAGEM SEGURA','18','17'),('SUSPENSÃO AUTOMÁTICA','19','19'),('SUSPENSAO AUTOMATICA','19','19'),('TRANSPARÊNCIA DE DADOS','20','19'),('TRANSPARENCIA DE DADOS','20','19'),('PERSONALIZAÇÃO DA INTERFACE','21','19'),('PERSONALIZACAO DA INTERFACE','21','19'),('GEORREFERENCIAMENTO','22','19'),('LOG DE ACESSOS','23','19'),('SISTEMA DE AVALIAÇÃO','24','20'),('SISTEMA DE AVALIACAO','24','20'),('ATRIBUIÇÃO DE RESPONSABILIDADE','25','20'),('ATRIBUICAO DE RESPONSABILIDADE','25','20'),('ASSUNTOS','26','20'),('BUSCA AVANÇADA','27','21'),('BUSCA AVANCADA','27','21'),('CONTRASSENHA','28','22'),('MARCADORES / TAGS','29','22'),('FLUXO DE VALOR','30','22'),('DEMANDAS EM MODO MAPA','31','22'),('ASSINATURA DIGITAL','32','23'),('EDITOR DE TEXTO','33','27'),('CLASSIFICAÇÃO E BUSCA POR ETIQUETA','34','29'),('CLASSIFICACAO E BUSCA POR ETIQUETA','34','29'),('EAD - SISTEMA','35','29'),('RELATÓRIOS DE PRODUTIVIDADE','36','30'),('RELATORIOS DE PRODUTIVIDADE','36','30'),('PLANO DE CLASSIFICAÇÃO','37','34'),('PLANO DE CLASSIFICACAO','37','34'),('SERVIÇO DE IMPLANTAÇÃO','38','35'),('SERVICO DE IMPLANTACAO','38','35'),('CONDIÇÕES GERAIS','39','36'),('CONDICOES GERAIS','39','36'),('SUPORTE','40','37'),('TESTES DE ACEITAÇÃO','41','38'),('TESTES DE ACEITACAO','41','38'),('TRAMITA GOV.BR','42','38'),('PAINEL DE INDICADORES','43','39'),('ATUALIZAÇÕES E MANUTENÇÃO','44','41'),('ATUALIZACOES E MANUTENCAO','44','41'),('OBRIGAÇÕES DA CONTRATADA','45','42'),('OBRIGACOES DA CONTRATADA','45','42'),
 ('MEMORANDO / COMUNICAÇÃO INTERNA','1','44'),('MEMORANDO / COMUNICACAO INTERNA','1','44'),('CIRCULAR','2','45'),('OUVIDORIA','3','45'),('PROTOCOLO ELETRÔNICO','4','48'),('PROTOCOLO ELETRONICO','4','48'),('E-SIC','5','51'),('OFÍCIO ELETRÔNICO','6','51'),('OFICIO ELETRONICO','6','51'),('PROCESSO ADMINISTRATIVO','7','52'),('ATOS OFICIAIS','8','55'),('APROVAÇÃO DE PROJETOS','9','56'),('APROVACAO DE PROJETOS','9','56'),('APLICATIVO MÓVEL','10','61'),('APLICATIVO MOVEL','10','61'),('WORKFLOW','11','62'),('ASSINATURA DIGITAL EM LOTE','12','63'),('PROCESSO ADMINISTRATIVO DISCIPLINAR','13','63'),('PROCESSO SELETIVO SIMPLIFICADO','14','64'),('PROTOCOLO LEGISLATIVO','15','65'),('REQUERIMENTO PESSOAL','16','65'),('CHAMADO TÉCNICO','16','66'),('CHAMADO TECNICO','16','66'),
]
heading_prefixes=('CARACTERISTICAS TECNICAS','CARACTERÍSTICAS TÉCNICAS','VISÃO GERAL','VISAO GERAL','ESTRUTURAÇÃO','ESTRUTURACAO','DIVISÃO POR MÓDULO','DIVISAO POR MODULO','ORGANOGRAMAS','CALENDÁRIO','CALENDARIO','SITUAÇÃO DOS DOCUMENTOS','SITUACAO DOS DOCUMENTOS','PÁGINA DE PLANO','PAGINA DE PLANO','GERENCIAMENTO DE CONTATOS','DEMANDAS DO SETOR','FUNCIONALIDADE DE QUEM','STATUS DE LEITURA','CENTRAL DE ATENDIMENTO','SISTEMA DE NOTIFICAÇÃO','SISTEMA DE NOTIFICACAO','HOSPEDAGEM SEGURA','SUSPENSÃO AUTOMÁTICA','SUSPENSAO AUTOMATICA','TRANSPARÊNCIA DE DADOS','TRANSPARENCIA DE DADOS','PERSONALIZAÇÃO DA INTERFACE','PERSONALIZACAO DA INTERFACE','GEORREFERENCIAMENTO','LOG DE ACESSOS','SISTEMA DE AVALIAÇÃO','SISTEMA DE AVALIACAO','ATRIBUIÇÃO DE RESPONSABILIDADE','ATRIBUICAO DE RESPONSABILIDADE','ASSUNTOS','BUSCA AVANÇADA','BUSCA AVANCADA','CONTRASSENHA','SISTEMA DE MARCADORES','FLUXO DE VALOR','DEMANDAS EM MODO MAPA','SISTEMA DE ASSINATURA','PARA O SISTEMA','CARACTERÍSTICAS GERAIS','CARACTERISTICAS GERAIS','EDITOR DE TEXTO','SISTEMA DE CLASSIFICAÇÃO','SISTEMA DE CLASSIFICACAO','EAD - SISTEMA','RELATÓRIOS DE PRODUTIVIDADE','RELATORIOS DE PRODUTIVIDADE','INFORMAÇÕES GERENCIAIS','INFORMACOES GERENCIAIS','PROGRESSO NO SISTEMA','QUANTITATIVO DE DOCUMENTOS','ACESSOS DIÁRIOS','ACESSOS DIARIOS','ESTATÍSTICA','ESTATISTICA','INFORMAÇÕES SOBRE','INFORMACOES SOBRE','GRÁFICOS DE UTILIZAÇÃO','GRAFICOS DE UTILIZACAO','RELATÓRIO GEORREFERENCIADO','RELATORIO GEORREFERENCIADO','PLANO DE CLASSIFICAÇÃO','PLANO DE CLASSIFICACAO','SERVIÇO DE IMPLANTAÇÃO','SERVICO DE IMPLANTACAO','CONDIÇÕES GERAIS','CONDICOES GERAIS','SUPORTE','INTEGRAÇÃO COM O TRAMITA','INTEGRACAO COM O TRAMITA','PAINEL DE INDICADORES','ATUALIZAÇÕES E MANUTENÇÃO','ATUALIZACOES E MANUTENCAO','OBRIGAÇÕES DA CONTRATADA','OBRIGACOES DA CONTRATADA','MEMORANDO / COMUNICAÇÃO INTERNA','MEMORANDO / COMUNICACAO INTERNA','CIRCULAR','OUVIDORIA','PROTOCOLO ELETRÔNICO','PROTOCOLO ELETRONICO','E-SIC','OFÍCIO ELETRÔNICO','OFICIO ELETRONICO','PROCESSO ADMINISTRATIVO','ATOS OFICIAIS','APROVAÇÃO DE PROJETOS','APROVACAO DE PROJETOS','APLICATIVO MÓVEL','APLICATIVO MOVEL','WORKFLOW','ASSINATURA DIGITAL EM LOTE','PROCESSO ADMINISTRATIVO DISCIPLINAR','PROCESSO SELETIVO SIMPLIFICADO','PROTOCOLO LEGISLATIVO','REQUERIMENTO PESSOAL','CHAMADO TÉCNICO','CHAMADO TECNICO')

def clean_line(line):
    s=line.strip()
    if not s or s.startswith(('Assinado por ','Para verificar a validade das assinaturas','Prefeitura Municipal de Mata de São João','Rua Luiz Antonio Garcez','Tel.:','Fl.')):
        return ''
    if 'www.matadesaojoao.ba.gov.br' in s or '1doc.com.br/verificacao' in s: return ''
    return re.sub(r'\s+',' ',s)

def heading(s):
    raw=s.strip()
    bare=raw.rstrip(':').strip()
    letters=[c for c in bare if c.isalpha()]
    ratio=sum(1 for c in letters if c.isupper())/len(letters) if letters else 0
    # Section labels are short, mostly uppercase, or exact introductory labels.
    if len(bare)<=110 and ratio>0.72: return True
    if len(bare)<=75 and raw.endswith(':') and not re.match(r'^(a contratada|a plataforma|o sistema|o acesso|permitir|possuir|haverá|sera|será|deverá|devera|capacidade de)', bare, re.I): return True
    title_prefixes=('Acesso interno à Plataforma','Acesso externo à Plataforma','Sistema em Cloud','Segurança - Métodos de acesso','Mediante este cadastro, é possível acessar áreas específicas para','Informações Gerenciais','Indicadores Visuais de Desempenho','Monitoramento de Usuários e Setores','Métricas de Eficiência e Engajamento','Análise de Processos e Documentos','Gráficos de Trâmite e Desempenho','Progresso no Sistema de Treinamento','Quantitativo de Documentos','Acessos Diários','Estatística','Informações sobre Consumo de Recursos','Gráficos de Utilização','Relatório Georreferenciado')
    return len(bare)<=90 and not re.search(r'[.;]$',raw) and any(bare.lower().startswith(x.lower()) for x in title_prefixes)

def normalized(s):
    return ''.join(c for c in unicodedata.normalize('NFD',s.lower()) if unicodedata.category(c)!='Mn')
def module_for(section, body):
    z=normalized(section+' '+body)
    if 'aprovacao de projetos' in z: return 'APROVACAO_PROJETOS'
    if 'ouvidoria' in z: return 'OUVIDORIA'
    if 'e-sic' in z: return 'E_SIC'
    if 'protocolo legislativo' in z: return 'PROTOCOLO_LEGISLATIVO'
    if 'requerimento pessoal' in z: return 'REQUERIMENTO_PESSOAL'
    if 'chamado tecnico' in z: return 'CHAMADO_TECNICO'
    if 'protocolo eletronico' in z or 'protocolo web' in z: return 'PROTOCOLO'
    if 'memorando' in z or 'circular' in z or 'oficio' in z or 'comunicacao interna' in z: return 'COMUNICACAO_OFICIAL'
    if 'processo administrativo' in z or 'workflow' in z or 'pad' in z: return 'PROCESSOS_ADMINISTRATIVOS'
    if 'ead' in z or 'treinamento de usuarios' in z: return 'TREINAMENTO'
    if 'assinatura' in z or 'certificado digital' in z or 'icp-brasil' in z: return 'ASSINATURAS'
    if 'suporte' in z or 'implantacao' in z or 'migracao' in z or 'tramitagov' in z or 'sisobra' in z: return 'OPERACAO_E_INTEGRACOES'
    if 'relatorio' in z or 'indicador' in z or 'painel' in z: return 'RELATORIOS_E_INDICADORES'
    if 'inteligencia artificial' in z or 'business intelligence' in z or 'grafico interativo' in z: return 'RELATORIOS_E_INDICADORES'
    if 'notificacao' in z: return 'NOTIFICACOES'
    if 'central de atendimento' in z or 'acesso externo' in z: return 'PORTAL_EXTERNO'
    if 'aplicativo movel' in z or 'aplicativo para atendimento' in z: return 'PORTAL_EXTERNO'
    if any(x in z for x in ('seguranca','auditoria','lgpd','privacidade','acesso','login','senha','autenticacao','permisso')): return 'IDENTIDADE_E_SEGURANCA'
    if any(x in z for x in ('organograma','setor','departamento','usuario','contato','organizacao')): return 'ADMINISTRACAO'
    if any(x in z for x in ('prazo','calendario','atividade')): return 'PRAZOS'
    if any(x in z for x in ('anexo','documento','arquivo','classificacao','etiqueta','busca','editor de texto','pdf/a','marcador','tags')): return 'GESTAO_DOCUMENTAL'
    if any(x in z for x in ('atos oficiais','lei municipal','decreto','portaria')): return 'ATOS_OFICIAIS'
    return 'PLATAFORMA_E_ARQUITETURA'

def tr_ref(section, edital_page):
    z=normalized(section)
    for key,num,p in tr_map:
        if normalized(key) in z:
            return f'Termo de Referência §{num}, PDF p.{p}'
    return ''

def classify(section, body):
    z=normalized(section+' '+body)
    if any(x in z for x in ('atestados','qualificacao tecnica','habilitacao','empresa desenvolvedora','revenda','representante autorizada')): return 'HABILITACAO_EMPRESARIAL'
    if any(x in z for x in ('implantacao','migracao','treinamento presencial','suporte','hospedagem','sla','data center','backup','pentest','consultoria especializada','atualizacoes','manutencao','tramitagov','tcm/ba','sisobra')): return 'OPERACAO_CONTRATUAL'
    if 'preferencialmente' in z: return 'PREFERENCIAL'
    return 'SOFTWARE'

items=[]
last_section='14.1.13 — Obrigações específicas da contratada para a execução dos serviços'
current=None
current_page=None
for pg in text.split('\f'):
    m=re.search(r'Fl\.\s*(\d+)\s*/\s*80',pg)
    if not m: continue
    page=int(m.group(1))
    if not (13<=page<=40):
        if current:
            items.append((current_page,last_section,current.strip()))
            current=None
        continue
    for raw in pg.splitlines():
        if raw.lstrip().startswith('\uf0b7'):
            if current:
                body=' '.join(current.split())
                if heading(body): last_section=body.rstrip(':').strip()
                else: items.append((current_page,last_section,body))
            current_page=page
            current=raw.lstrip()[1:].strip()
        elif current is not None:
            s=clean_line(raw)
            if s: current+=' '+s
if current:
    body=' '.join(current.split())
    if heading(body): last_section=body.rstrip(':').strip()
    else: items.append((current_page,last_section,body))

rows=[]
for page,section,body in items:
    mod=module_for(section,body)
    cat=classify(section,body)
    idx=len(rows)+1
    rid=f'ED91-{idx:04d}'
    # Preserve the source bullet text. Text is transcribed from the searchable PDF.
    acceptance=f'Comprovar, no NexoGov, a capacidade descrita no requisito com dados fictícios e evidência observável; respeitar os controles de acesso aplicáveis.'
    if cat=='OPERACAO_CONTRATUAL':
        acceptance='Apresentar evidência operacional/contratual da obrigação e demonstrar no sistema qualquer parte que dependa de software.'
        test='Não comprovável apenas por teste de software; evidência operacional/contratual pendente.'
    elif cat=='HABILITACAO_EMPRESARIAL':
        acceptance='Apresentar documento de habilitação válido que satisfaça a condição descrita; avaliação jurídica/comercial externa ao software.'
        test='Não aplicável a teste de software; documento empresarial pendente de avaliação.'
    else:
        test=f'Pendente: teste de integração ou E2E específico para {rid}; sem teste executado.'
    secondary=tr_ref(section,page)
    source=f'Edital PE 91/2025, §14.1.13, fl. impressa {page}, subtítulo “{section}”'
    if secondary: source+=f'; {secondary}'
    source+='.'
    priority='PREFERENCIAL' if cat=='PREFERENCIAL' else 'OBRIGATÓRIO'
    evidence='Ainda sem evidência no NexoGov.'
    poc=f'POC-{mod}: exercitar o comportamento descrito com dados fictícios, validar o resultado e registrar evidência; roteiro detalhado em POC.md.'
    backend='API/serviço e persistência do NexoGov' if cat=='SOFTWARE' else 'Não aplicável ou integração operacional, conforme requisito'
    frontend='Interface web responsiva do NexoGov' if cat=='SOFTWARE' else 'Portal/tela demonstrativa somente se o requisito exigir'
    rows.append([rid,body,source,priority,mod,cat,acceptance,backend,frontend,test,poc,evidence,'NAO_INICIADO'])

# Cross-cutting contractual, POC, qualification and capacity requirements not represented as feature bullets.
manual=[
 ['9.7.3.1','Comprovação por atestado(s) de pessoa jurídica pública ou privada de atuação em ramo pertinente e execução satisfatória de contrato anterior.','Edital §9.7.3.1, fl. 9; TR §18, PDF p.67.','OBRIGATÓRIO','HABILITACAO_EMPRESARIAL','Apresentar e validar atestado compatível, incluindo sustentação da solução quando exigida.','Não aplicável a software; evidência documental empresarial pendente.'],
 ['9.7.3.2','Participação permitida a desenvolvedora ou revenda/representante autorizada, legalmente estabelecida no país.','Edital §9.7.3.2, fl. 9; TR §18, PDF p.67.','OBRIGATÓRIO','HABILITACAO_EMPRESARIAL','Comprovar condição de desenvolvedora ou de representante autorizado.','Não aplicável a software; evidência documental empresarial pendente.'],
 ['9.7.3.3','Revenda/representante deve apresentar contrato vigente pelo período previsto para atendimento do contrato administrativo.','Edital §9.7.3.3, fl. 9; TR §18, PDF p.67.','OBRIGATÓRIO','HABILITACAO_EMPRESARIAL','Contrato válido cobrindo o prazo contratual aplicável.','Não aplicável a software; evidência documental empresarial pendente.'],
 ['9.9.0','Realizar POC após conformidade da proposta/habilitação, em até 72 horas úteis após convocação/agendamento, presencial ou remota; licitante fornece equipamentos e internet; equipe técnica emite laudo.','Edital §9.9, fl. 10; TR §41, PDF p.38; Errata I, p.2 para alteração de duração.','OBRIGATÓRIO','POC_E_OPERACAO','Demonstrar o sistema completo dentro do prazo convocado, com ambiente e equipamentos disponíveis, e registrar o laudo.','Não comprovável por teste automatizado; execução e laudo da POC pendentes.'],
 ['9.9.1','Demonstração avalia compatibilidade prática e permite questionamentos; Errata I substitui o limite de 90 minutos por duração de 90 minutos prorrogável.','Edital §9.9.1, fl. 10; Errata I §1, p.1.','OBRIGATÓRIO','POC','Realizar roteiro integral em 90 minutos, com plano de extensão e respostas a questionamentos técnicos.','Cronometrar ensaio e registrar cobertura; ensaio pendente.'],
 ['9.9.2','A análise considera conformidade com especificações; Administração pode pedir informações adicionais; rejeição ou não apresentação convoca próximas classificadas.','Edital §9.9.2, fl. 10; Errata I §1 altera duração, sem modificar demais critérios.','OBRIGATÓRIO','POC_E_OPERACAO','Roteiro, evidências e equipe devem permitir responder solicitações e demonstrar cada requisito.','Não aplicável a teste de software isolado; ensaio e evidências pendentes.'],
 ['13.1','Implantação e treinamento em até 30 dias corridos após Ordem de Serviço; suporte contínuo até o fim da vigência.','Edital §13.1, fl. 12; TR §38, PDF p.35; TR §40, PDF pp.37–38.','OBRIGATÓRIO','IMPLANTACAO_E_SUPORTE','Plano e cronograma comprovam implantação/treinamento no prazo e suporte contínuo.','Não aplicável a teste automatizado; plano de serviço e evidências de execução pendentes.'],
 ['TR-2','Licenciar 600 usuários internos, com margem de acréscimo/supressão de até 15%, e usuários externos ilimitados.','Termo de Referência §2, PDF p.1; OCR de apoio.','OBRIGATÓRIO','CAPACIDADE_E_LICENCIAMENTO','Demonstrar capacidade/licenciamento para 510–690 usuários internos e usuários externos sem limite contratual declarado.','Pendente: teste de carga para capacidade interna; controle de licenças e evidência contratual.'],
 ['TR-38','Converter e migrar dados atualmente existentes e informações de aplicativos em uso, com apoio da contratada.','Termo de Referência §38, PDF pp.35–36; Edital §14.1.13, fl. 28.','OBRIGATÓRIO','MIGRACAO','Executar migração rastreável, validar reconciliação e apresentar relatório de erros e registros migrados.','Pendente: teste de integração com dados fictícios; execução real depende de inventário/autorização do contratante.'],
 ['TR-40','Prestar suporte técnico, incluindo canal pela ferramenta, atendimento remoto e cobertura em dias úteis/horário comercial, segundo requisitos do termo.','Termo de Referência §40, PDF pp.37–38; Edital §14.1.13, fl. 28.','OBRIGATÓRIO','SUPORTE','Demonstrar canal e relatórios do serviço; comprovar SLA e horários por operação.','Não comprovável apenas por teste automatizado; processo e evidência operacional pendentes.'],
 ['CONTRATO-24M','Vigência contratual prevista de 24 meses.','Edital preâmbulo, fl. 1; Termo de Referência e página oficial do certame.','OBRIGATÓRIO','CONTRATUAL','Comprovar capacidade de operação, suporte, atualização, backup e continuidade durante a vigência.','Não aplicável a teste de software; obrigação comercial/operacional.'],
]
for idx,(item,req,source,priority,mod,accept,test) in enumerate(manual, start=1):
    rid=f'ED91-X{idx:03d}'
    rows.append([rid,req,source,priority,mod,'EXTERNO/CONTRATUAL',accept,'N/A ou serviços técnicos conforme obrigação','N/A ou ferramenta de operação conforme obrigação',test,f'POC-OPERACAO: apresentar documentos/serviços ou demonstrar a parte software; ver POC.md.', 'Ainda sem evidência verificável.', 'NAO_INICIADO'])

fields=['id','requisito_resumo_fiel','origem_documento_pagina_secao_item','prioridade','modulo','natureza','criterio_de_aceite','backend_envolvido','frontend_envolvido','teste_automatizado','roteiro_poc','evidencia','status']
out=base/'MATRIZ_ADERENCIA.csv'
with out.open('w',encoding='utf-8-sig',newline='') as f:
    w=csv.writer(f,delimiter=';',quoting=csv.QUOTE_MINIMAL)
    w.writerow(fields); w.writerows(rows)
print('requirements',len(rows),'source_bullets',len(items),'modules',len(set(r[4] for r in rows)))
from collections import Counter
print('by module',dict(Counter(r[4] for r in rows)))

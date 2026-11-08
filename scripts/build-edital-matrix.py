import csv, re, unicodedata
from collections import Counter
from pathlib import Path
from matrix_id_registry import MatrixIdRegistry
root=Path(__file__).resolve().parents[1]
base=root/'docs/licitacoes/mata-de-sao-joao-91-2025'
out=base/'MATRIZ_ADERENCIA.csv'
id_registry=MatrixIdRegistry(base/'MATRIZ_IDS.csv')
previous_progress={}
if out.exists():
    with out.open(encoding='utf-8-sig',newline='') as f:
        previous_progress={r['id']:r for r in csv.DictReader(f,delimiter=';')}
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
    if 'pentest whitebox' in z or 'teste regular de seguranca' in z:
        return 'IDENTIDADE_E_SEGURANCA'
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
    # A broad source phrase about managing activities describes the platform,
    # not deadline/calendar handling. Keep it out of the PRAZOS bucket.
    if 'gerenciamento de atividades' in z and not any(x in z for x in ('prazo','calendario')): return 'PLATAFORMA_E_ARQUITETURA'
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
identity_occurrences=Counter()
for page,section,body in items:
    mod=module_for(section,body)
    cat=classify(section,body)
    page_ref=f'p.{page}'
    base_identity=id_registry.identity('ED91',page_ref,body)[0]
    identity_occurrences[base_identity]+=1
    rid=id_registry.assign('ED91',page_ref,body,identity_occurrences[base_identity])
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
    if rid == 'ED91-0031':
        # The pentest bullet is on scanned TR PDF page 4, not page 3 where §4 begins.
        secondary='Termo de Referência §4, PDF p.4'
    if rid == 'ED91-0481':
        # The referenced §4 continues across the scanned PDF pages 48 and 49.
        secondary='Termo de Referência §4, PDF pp.48–49'
    if rid == 'ED91-0483':
        # Completion/reopening text appears on the scanned TR page 49.
        secondary='Termo de Referência §4, PDF p.49'
    if rid == 'ED91-0484':
        # Attachment formats continue onto the scanned TR page 50.
        secondary='Termo de Referência §4, PDF pp.49–50'
    if rid == 'ED91-0488':
        # The participant-identification and interaction text is on scanned TR page 50.
        secondary='Termo de Referência §4, PDF p.50'
    if rid == 'ED91-0486':
        # Verified claimant login by ICP-Brasil/Gov.br is in the same protocol section on page 50.
        secondary='Termo de Referência §4, PDF p.50'
    if rid == 'ED91-0120':
        secondary='Termo de Referência §10, PDF p.12'
    if rid == 'ED91-0140':
        secondary='Termo de Referência §15, PDF p.14'
    source=f'Edital PE 91/2025, §14.1.13, fl. impressa {page}, subtítulo “{section}”'
    if secondary: source+=f'; {secondary}'
    source+='.'
    priority='PREFERENCIAL' if cat=='PREFERENCIAL' else 'OBRIGATÓRIO'
    evidence='Ainda sem evidência no NexoGov.'
    poc=f'POC-{mod}: exercitar o comportamento descrito com dados fictícios, validar o resultado e registrar evidência; roteiro detalhado em POC.md.'
    backend='API/serviço e persistência do NexoGov' if cat=='SOFTWARE' else 'Não aplicável ou integração operacional, conforme requisito'
    frontend='Interface web responsiva do NexoGov' if cat=='SOFTWARE' else 'Portal/tela demonstrativa somente se o requisito exigir'
    rows.append([rid,body,source,priority,mod,cat,acceptance,backend,frontend,test,poc,evidence,'NAO_INICIADO','CONFERIDA_NO_DOCUMENTO_OFICIAL'])
    if rid == 'ED91-0031':
        rows[-1][10] = (
            'POC-PENTEST-OPERACIONAL: para uma avaliação futura autorizada, apresentar relatórios de Pentest Whitebox '
            'de consultoria especializada com certificação em segurança, com intervalo máximo de três meses e '
            'detalhamento de métodos, técnicas, ferramentas, vulnerabilidades encontradas e recomendações; '
            'ou apresentar metodologia alternativa e cronograma sujeitos à validação do Departamento de TI. '
            'Os testes automatizados locais não evidenciam prestação desse serviço. O Pregão 91/2025 já tem '
            'adjudicação e contrato publicados; esta linha não constitui convocação vigente, e não há evidência '
            'de pentest contratual executado pelo NexoGov.'
        )

# Cross-cutting contractual, POC, qualification and capacity requirements not represented as feature bullets.
manual=[
 ['9.7.3.1','Comprovação por atestado(s) de pessoa jurídica pública ou privada de atuação em ramo pertinente e execução satisfatória de contrato anterior.','Edital §9.7.3.1, fl. 9; TR §18, PDF p.67.','OBRIGATÓRIO','HABILITACAO_EMPRESARIAL','Apresentar e validar atestado compatível, incluindo sustentação da solução quando exigida.','Não aplicável a software; evidência documental empresarial pendente.'],
 ['9.7.3.2','Participação permitida a desenvolvedora ou revenda/representante autorizada, legalmente estabelecida no país.','Edital §9.7.3.2, fl. 9; TR §18, PDF p.67.','OBRIGATÓRIO','HABILITACAO_EMPRESARIAL','Comprovar condição de desenvolvedora ou de representante autorizado.','Não aplicável a software; evidência documental empresarial pendente.'],
 ['9.7.3.3','Revenda/representante deve apresentar contrato vigente pelo período previsto para atendimento do contrato administrativo.','Edital §9.7.3.3, fl. 9; TR §18, PDF p.67.','OBRIGATÓRIO','HABILITACAO_EMPRESARIAL','Contrato válido cobrindo o prazo contratual aplicável.','Não aplicável a software; evidência documental empresarial pendente.'],
 ['9.9.0','Realizar POC após conformidade da proposta/habilitação, em até 72 horas úteis após agendamento; solicitar confirmação do e-mail e usar telefone se não houver retorno; sessão pública presencial ou remota; licitante fornece material; equipe técnica emite laudo.','Edital §9.9, fl. 10; TR §41, PDF p.38; Errata I §1, p.1 (duração prorrogável) e p.2 (agendamento e confirmação).','OBRIGATÓRIO','POC_E_OPERACAO','Após convocação, agendar e apresentar em até 72 horas úteis; enviar solicitação para suporte@pmsj.ba.gov.br e pedir confirmação, ligar para (71) 3635-1310 se não houver retorno, preparar sessão pública presencial/remota e materiais próprios, demonstrar os requisitos obrigatórios dentro de 90 minutos prorrogáveis e registrar o laudo técnico.','Não comprovável por teste automatizado; checklist operacional e ensaio cronometrado pendentes.'],
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
    rows.append([rid,req,source,priority,mod,'EXTERNO/CONTRATUAL',accept,'N/A ou serviços técnicos conforme obrigação','N/A ou ferramenta de operação conforme obrigação',test,f'POC-OPERACAO: apresentar documentos/serviços ou demonstrar a parte software; ver POC.md.', 'Ainda sem evidência verificável.', 'NAO_INICIADO','CONFERIDA_NO_DOCUMENTO_OFICIAL'])
    if rid in {'ED91-X001', 'ED91-X002', 'ED91-X003'}:
        rows[-1][5] = 'HABILITACAO_EMPRESARIAL'
        rows[-1][10] = (
            'VERIFICACAO-HABILITACAO: conferir a documentação da licitante na fase de habilitação; '
            'são evidências empresariais/jurídicas, sem demonstração em tela ou teste de software.'
        )
    if rid == 'ED91-X004':
        rows[-1][6] = (
            'Condição procedimental da sessão de 2025: POC após análise da proposta/habilitação, com prazo e '
            'condições alterados pela Errata I. A página oficial registra adjudicação e contrato; não há '
            'convocação vigente para este certame nem laudo de POC do NexoGov.'
        )
        rows[-1][10] = (
            'POC-HISTORICA-91-2025: esta linha registra o procedimento do edital/errata de 2025. '
            'A página oficial lista adjudicação, homologação e contrato publicado; não é convocação vigente. '
            'A POC integral do NexoGov não foi executada e não há laudo próprio; para outro certame, consultar '
            'o novo edital e a convocação escrita antes de reutilizar o roteiro.'
        )
        rows[-1][11] = (
            'Condições históricas documentadas em POC.md; não há laudo da comissão da POC do NexoGov. '
            'Adjudicação/contrato da licitação não são evidência de demonstração pelo NexoGov.'
        )
    if rid in {'ED91-X005', 'ED91-X006'}:
        if rid == 'ED91-X005':
            rows[-1][6] = (
                'Regra histórica da sessão de 2025: a Errata I alterou a duração para 90 minutos, prorrogáveis. '
                'A linha não cria convocação atual nem comprova demonstração ou laudo do NexoGov.'
            )
        else:
            rows[-1][6] = (
                'Regra histórica do procedimento de avaliação; não há convocação atual ou laudo da POC do NexoGov '
                'nos artefatos disponíveis.'
            )
        rows[-1][10] = (
            'POC-HISTORICA-91-2025: requisito procedimental do certame encerrado, registrado como referência. '
            'A matriz não contém convocação atual nem laudo da POC do NexoGov. Aplicar regras de um edital futuro '
            'somente após conferir sua publicação e convocação.'
        )

partial_organization_requirements = {
    'ED91-0009': 'O backend cria e lista setores da organização. Lista de contatos e organograma oficial ainda não estão implementados.',
    'ED91-0087': 'O backend cria setores isolados por organização; hierarquia com subsetores e profundidade ilimitada ainda não está implementada.',
    'ED91-0103': 'O backend mantém setores da organização e a interface os lista; organograma dinâmico de setores e usuários ainda não está implementado.',
    'ED91-0104': 'O backend cria setores da organização; relacionamento pai/filho para subsetores ilimitados ainda não está implementado.',
}
partial_member_access_requirements = {
    'ED91-0042': (
        'Parcial — o administrador organizacional pode consultar membros e suspender/reativar somente o acesso à organização, com auditoria e retenção de conta, memberships e histórico. Provisionamento de contas e vínculos ainda não está implementado.',
        'OrganizationAdminService/Controller e organization_member_access_status; contexto de tenant validado no filtro e papel ADMIN organizacional verificado no servidor.',
        'Área do servidor: lista membros e apresenta papéis organizacionais e de setor separadamente; oferece suspensão confirmada e reativação somente conforme a resposta da API.',
        'OrganizationMemberAccessIntegrationTest (PostgreSQL Testcontainers): listagem, ADMIN setorial sem poder suspender (403), alvo exclusivo de outro tenant negado (404), suspensão, reativação, preservação e último administrador; organization-members.spec.ts: confirmação, ações com nomes acessíveis e erro de ação separado da falha de carregamento. Provisionamento completo continua pendente.',
        'POC-ADMIN-USUARIOS: listar membros fictícios e conferir papéis separados; provar que ADMIN apenas setorial não suspende membro e que alvo exclusivo de outra organização é recusado sem alteração; conferir nomes acessíveis das ações. Criação de contas permanece pendente.',
        'docs/INCREMENTO_20.md, docs/INCREMENTO_21.md, docs/INCREMENTO_23.md, OrganizationMemberAccessIntegrationTest e organization-members.spec.ts; fluxo de criação/provisionamento de contas continua sem evidência.',
    ),
    'ED91-0046': (
        'Parcial — o administrador pode suspender e reativar o acesso do servidor somente na organização atual; memberships de setor/organização, conta global e histórico permanecem. A exclusão de cadastro não é oferecida por esta API, mas não há garantia transversal de imutabilidade contra operações externas ao fluxo.',
        'OrganizationAdminService/Controller, V7 organization_member_access_status e TenantMembershipFilter; a suspensão é aplicada em cada requisição protegida e não altera user_accounts.active nem memberships.',
        'Área do servidor permite suspender com confirmação e reativar o acesso à organização; estados refletem a resposta da API e o último administrador ativo fica protegido também no backend.',
        'OrganizationMemberAccessIntegrationTest verifica suspensão negada para ADMIN apenas setorial (403), alvo exclusivo de outro tenant (404), suspensão/reentrada após autenticação, idempotência, reativação, preservação, auditoria e proteção concorrente; organization-members.spec.ts verifica ações com nomes acessíveis e apresenta falha de ação separada da falha de carregamento.',
        'POC-ADMIN-ACESSO: suspender membro fictício e validar 403 na requisição autenticada seguinte; conferir conta, memberships e histórico preservados e reativar. Repetir uma tentativa de suspensão com ADMIN apenas setorial e alvo de outro tenant, verificar 403/404 sem estado ou auditoria alterados; conferir os nomes acessíveis e a mensagem de ação recusada com atualização explícita da lista. Provisionamento permanece pendente.',
        'docs/INCREMENTO_20.md, docs/INCREMENTO_21.md, docs/INCREMENTO_23.md, testes PostgreSQL Testcontainers e organization-members.spec.ts; não existe mecanismo global de exclusão de cadastro nem IdP real homologado.',
    ),
}
federated_identity_backend = (
    'SecurityConfiguration valida assinatura RS256 por JWKS, issuer, audience e validade; issuer e JWKS exigem HTTPS, com HTTP permitido apenas para loopback no perfil de teste; '
    'JdbcExternalIdentityResolver resolve vínculo ativo por organization_id + issuer + subject somente quando user_accounts.active=true; '
    'TenantMembershipFilter exige organização e membership ativa e recusa suspensão organizacional. '
    'V9 bloqueia links globais legados sem backfill/reconciliação.'
)
federated_identity_frontend = (
    'A área do servidor implementa cliente público OIDC Authorization Code + PKCE, com validação de state/nonce e callback configurado; '
    'Basic só é selecionado explicitamente em desenvolvimento, e sem configuração OIDC válida a interface não inicia o acesso. '
    'O access token segue em Bearer e fica apenas em memória. Logout limpa a sessão local e não afirma revogação server-side do token. Os testes usam um provedor simulado; IdP municipal, provisionamento, '
    'login por e-mail/senha e recuperação ainda estão pendentes.'
)
federated_identity_test = (
    'FederatedIdentityIntegrationTest: vínculo ativo por organização com mesmo username/subject em Alfa e Beta, '
    'conta local ativa exigida e recusa em todos os tenants quando user_accounts.active=false, link ausente/inativo, membership ausente, suspensão, issuer/audience/exp inválidos; '
    'MigrationSafetyIntegrationTest#blocksGlobalExternalIdentityLinksWithoutChangingLegacyData cobre V9 sem backfill. '
    'As suítes são executadas por ./dev identity-test com PostgreSQL Testcontainers e JWKS RSA temporário. '
    'staff-oidc.spec.ts, executado por ./dev oidc-test, valida state, nonce, PKCE, bearer, logout e callback com IdP simulado; '
    'não comprova integração com provedor municipal.'
)
federated_identity_route = (
    'POC-AUTH-FEDERADA-V9: executar ./dev identity-test. Demonstrar o mesmo username e issuer+subject em Alfa e Beta, '
    'com conta local ativa, link ativo em Alfa e inativo em Beta; confirmar 200 em Alfa e 403 em Beta. Definir user_accounts.active=false e confirmar 403 em ambos; reativar e confirmar 200 em Alfa. '
    'Demonstrar recusa sem link, sem membership e após suspensão; rejeitar issuer, audience e exp inválidos. '
    'No teste de migração, partir de V8 com um link global legado: V9 deve falhar sem converter/remover o link ou alterar memberships/conta. '
    'Para o cliente da interface, executar ./dev oidc-test: verificar Authorization Code + PKCE S256, state, nonce, callback, '
    'bearer e logout com provedor simulado. Logout não comprova revogação server-side do access token. Isso não prova homologação/integração com IdP municipal nem permissões documentais completas.'
)
federated_identity_evidence = (
    'V9__organization_scoped_external_identity.sql; FederatedIdentityIntegrationTest e '
    'MigrationSafetyIntegrationTest#blocksGlobalExternalIdentityLinksWithoutChangingLegacyData; comando ./dev identity-test. '
    'docs/INCREMENTO_30.md e staff-oidc.spec.ts (./dev oidc-test; provedor simulado). Cobertura não comprova IdP real, '
    'provisionamento, login por e-mail/senha ou autorização documental completa.'
)
partial_federated_identity_requirements = {
    'ED91-0035': (
        'Parcial — o serviço de recursos valida o JWT e resolve um vínculo ativo issuer+subject na organização selecionada; membership ativa e suspensão da organização são verificadas no backend. Isso não fornece o cadastro administrativo de senha pessoal exigido neste requisito. IdP municipal/cliente OIDC real, provisionamento, login por e-mail/senha e autorização documental completa seguem pendentes.',
        federated_identity_backend, federated_identity_frontend, federated_identity_test, federated_identity_route, federated_identity_evidence,
    ),
    'ED91-0038': (
        'Parcial — JWT/JWKS/issuer/audience e vínculo ativo issuer+subject por organização têm validação no backend. Cadastro de e-mail individual, senha pessoal pelo administrador, recuperação e cliente OIDC municipal não estão integrados; a autorização documental completa também permanece pendente.',
        federated_identity_backend, federated_identity_frontend, federated_identity_test, federated_identity_route, federated_identity_evidence,
    ),
    'ED91-0059': (
        'Parcial — identidade federada e membership ativa/suspensão são verificadas no escopo da organização, e claims/roles externas não concedem autorização NexoGov. A segmentação completa por documento, grupo e nível de acesso conforme e-ARQ não está implementada; IdP OIDC real e provisionamento continuam pendentes.',
        federated_identity_backend + ' A autorização por recurso/documento ainda exige implementação específica de domínio.',
        federated_identity_frontend + ' Não há interface para configurar todos os níveis de acesso documental.',
        federated_identity_test + ' Estes testes não cobrem políticas completas de leitura documental, grupo ou classificação.',
        federated_identity_route + ' Demonstrar autorização de documentos somente após existir política completa, que permanece pendente.',
        federated_identity_evidence + ' Sem evidência de matriz completa de autorização documental.',
    ),
}
for row in rows:
    if row[0] in partial_organization_requirements:
        row[6] = 'Parcial — ' + partial_organization_requirements[row[0]]
        row[7] = 'OrganizationAdminService, OrganizationAdminController e tabela departments com escopo validado por TenantContext.'
        row[8] = 'Área do servidor: cadastro e seleção de setores retornados pela organização atual.'
        row[9] = 'ProtocolVerticalIntegrationTest.departmentAdministrationIsTenantScopedAdminOnlyAndAudited e teste E2E do cadastro de setor; cobertura da hierarquia ainda pendente.'
        row[10] = 'POC-ADMIN-ORGANIZACAO: autenticar como ADMIN, cadastrar setor, atualizar lista e demonstrar que outra organização não vê o novo setor.'
        row[11] = 'Parcial: teste PostgreSQL confirma criação autorizada, isolamento entre organizações e trilha append-only; hierarquia, contatos e organograma visual pendentes conforme o requisito.'
        row[12] = 'EM_DESENVOLVIMENTO'

    if row[0] in partial_member_access_requirements:
        (row[6], row[7], row[8], row[9], row[10], row[11]) = partial_member_access_requirements[row[0]]
        row[12] = 'EM_DESENVOLVIMENTO'

    if row[0] in partial_federated_identity_requirements:
        (row[6], row[7], row[8], row[9], row[10], row[11]) = partial_federated_identity_requirements[row[0]]
        row[12] = 'EM_DESENVOLVIMENTO'

partial_protocol_access_requirements = {
    'ED91-0062': 'Parcial — transições de protocolo exigem vínculo ativo com o setor atualmente responsável e serializam a atualização do estado com o evento; hierarquia de níveis 1/2/3, auditoria e outros tipos documentais ainda não estão implementados.',
    'ED91-0063': 'Parcial — a caixa de entrada lista protocolos dos setores com vínculo ativo; administrador da organização vê todos. Leitura de documentos/anexos e os níveis de acesso completos ainda não estão implementados.',
}
partial_protocol_read_status_requirements = {
    'ED91-0120': (
        'Parcial — a caixa de entrada distingue protocolo ainda não lido por nenhum servidor habilitado do setor, lido por outro servidor do setor e lido pelo próprio servidor. A marca é persistida somente depois de abrir o conteúdo do protocolo, e o estado é calculado para o setor atualmente responsável. A matriz ainda exige identificação por documento individual; histórico nominal de leitura, documentos de outros módulos e consulta de downloads permanecem sem implementação ou prova.',
        'ProtocolService.recordRead, ProtocolReadReceipt e V21 persistem uma leitura idempotente por organização, protocolo, setor atual e servidor; a lista projeta o estado sem mutação. O backend exige membership ativa no setor atribuído e valida a organização no TenantContext.',
        'StaffApp mostra os três estados em texto na caixa e registra leitura após carregar as mensagens do protocolo. `protocol-read-status.spec.ts` usa dois servidores fictícios da mesma lotação; não representa leitura individual de cada anexo ou documento.',
        'ProtocolVerticalIntegrationTest#protocolInboxReadReceiptIsIdempotentAndTenantAndDepartmentScoped (PostgreSQL Testcontainers), `./dev protocol-read-status-test`, e `protocol-read-status.spec.ts` via `./dev protocol-read-status-e2e`; a E2E comprova listagem sem mutação, estado de outro servidor, leitura própria e persistência após recarga. O escopo continua por protocolo, não por documento individual.',
        'POC-LEITURA-CAIXA (parcial): abrir um protocolo fictício; consultar a caixa antes de abrir o conteúdo e confirmar que a lista não registra leitura; abrir o conteúdo como um segundo servidor da mesma lotação; atualizar a caixa do primeiro servidor e conferir “outro servidor leu”; abrir o conteúdo como o primeiro servidor e conferir “você leu”; recarregar e confirmar a persistência. Demonstrar também recusa cross-tenant e de outra lotação por teste automatizado. O status cobre o protocolo, não todos os documentos.',
        'Capturas `leitura-nao-iniciada`, `leitura-por-colega` e `leitura-pelo-servidor` em `frontend/e2e/protocol-read-status.spec.ts`; teste PostgreSQL Testcontainers cobre idempotência, listagem sem gravação, isolamento e autorização. A imagem não prova leitura individual de cada documento, trilha nominal, contagem de downloads, outros módulos ou POC oficial.',
    ),
    'ED91-0140': (
        'Parcial — a caixa de entrada distingue protocolo ainda não lido por nenhum servidor habilitado do setor, lido por outro servidor do setor e lido pelo próprio servidor. A marca é persistida somente depois de abrir o conteúdo do protocolo, e o estado é calculado para o setor atualmente responsável. A matriz ainda exige identificação por documento individual; histórico nominal de leitura, documentos de outros módulos e consulta de downloads permanecem sem implementação ou prova.',
        'ProtocolService.recordRead, ProtocolReadReceipt e V21 persistem uma leitura idempotente por organização, protocolo, setor atual e servidor; a lista projeta o estado sem mutação. O backend exige membership ativa no setor atribuído e valida a organização no TenantContext.',
        'StaffApp mostra os três estados em texto na caixa e registra leitura após carregar as mensagens do protocolo. `protocol-read-status.spec.ts` usa dois servidores fictícios da mesma lotação; não representa leitura individual de cada anexo ou documento.',
        'ProtocolVerticalIntegrationTest#protocolInboxReadReceiptIsIdempotentAndTenantAndDepartmentScoped (PostgreSQL Testcontainers), `./dev protocol-read-status-test`, e `protocol-read-status.spec.ts` via `./dev protocol-read-status-e2e`; a E2E comprova listagem sem mutação, estado de outro servidor, leitura própria e persistência após recarga. O escopo continua por protocolo, não por documento individual.',
        'POC-LEITURA-CAIXA (parcial): abrir um protocolo fictício; consultar a caixa antes de abrir o conteúdo e confirmar que a lista não registra leitura; abrir o conteúdo como um segundo servidor da mesma lotação; atualizar a caixa do primeiro servidor e conferir “outro servidor leu”; abrir o conteúdo como o primeiro servidor e conferir “você leu”; recarregar e confirmar a persistência. Demonstrar também recusa cross-tenant e de outra lotação por teste automatizado. O status cobre o protocolo, não todos os documentos.',
        'Capturas `leitura-nao-iniciada`, `leitura-por-colega` e `leitura-pelo-servidor` em `frontend/e2e/protocol-read-status.spec.ts`; teste PostgreSQL Testcontainers cobre idempotência, listagem sem gravação, isolamento e autorização. A imagem não prova leitura individual de cada documento, trilha nominal, contagem de downloads, outros módulos ou POC oficial.',
    ),
}
partial_protocol_subject_requirements = {
    'ED91-0152': (
        'Parcial — o cidadão seleciona um assunto mantido pela organização; assuntos configurados para balcão não aparecem no portal e o backend rejeita ID presencial ou de outra organização antes de reservar numeração. Nome do assunto fica como snapshot no protocolo. Campos obrigatórios, documentos por assunto, mapas e demais regras da Central ainda não foram implementados.',
        'ProtocolService valida organização, ID do assunto, disponibilidade online e bloqueia a linha antes de consumir protocol_counters; V8 preserva protocolos antigos com protocol_subject_id nulo e snapshot subject.',
        'Portal do cidadão carrega assuntos online da organização selecionada; área administrativa permite cadastrar e alternar assuntos entre Central de Atendimento e balcão.',
        'ProtocolVerticalIntegrationTest.protocolSubjectCatalogIsTenantScopedOnlineOnlyAndKeepsSnapshots (PostgreSQL Testcontainers) e protocol-subject-catalog.spec.ts (Playwright com API/PostgreSQL reais); protocolo-flow.spec.ts cobre a seleção pública existente.',
        'POC-ASSUNTOS-PROTOCOLO: autenticar ADMIN, criar um assunto online e outro somente de balcão; confirmar que apenas o online aparece na Central, abrir protocolo com ele, tentar enviar ID presencial e ID de outra organização e conferir rejeição sem consumir número. Anexos, campos por assunto, mapa e demais critérios permanecem pendentes.',
        'Evidência parcial em docs/INCREMENTO_22.md, ProtocolVerticalIntegrationTest e protocol-subject-catalog.spec.ts; não demonstra toda a Central de Atendimento nem a POC integral.',
    ),
    'ED91-0487': (
        'Parcial — ADMIN configura assuntos como online ou somente balcão e o portal omite os presenciais. O item também exige mapa interativo/georreferenciamento em assuntos; coleta por mapa e marcador ainda não estão implementadas.',
        'ProtocolSubjectService e ProtocolService aplicam organização/ADMIN e rejeitam assunto presencial ou de outro tenant antes da numeração; V8 registra o catálogo e vincula protocolos novos sem reescrever snapshots legados. Georreferenciamento pendente.',
        'Área do servidor configura disponibilidade do assunto; portal público lista apenas itens online. Interface de mapa/georreferenciamento pendente.',
        'ProtocolVerticalIntegrationTest.protocolSubjectCatalogIsTenantScopedOnlineOnlyAndKeepsSnapshots e protocol-subject-catalog.spec.ts (API/PostgreSQL reais); não há teste de mapa/georreferenciamento.',
        'POC-ASSUNTOS-PROTOCOLO: demonstrar assuntos online e presenciais, tentativa inválida antes da numeração e isolamento entre organizações. Para concluir ED91-0487 ainda falta demonstrar coleta de localização em mapa com marcador.',
        'Evidência parcial em docs/INCREMENTO_22.md; a parte de mapa/georreferenciamento segue sem implementação ou evidência.',
    ),
}
partial_protocol_attachment_requirements = {
    'ED91-0484': (
        'Parcial — a configuração por assunto de documentos obrigatórios/opcionais e extensões permitidas está implementada para o protocolo inicial; o backend rejeita arquivo obrigatório ausente ou extensão não permitida antes de reservar número e persiste anexos em storage compatível com S3, entregues por rotas autorizadas da aplicação. A interface de interações posteriores também consulta formatos configurados e tem cenário E2E com PDF. O prazo automático exibido no comprovante está coberto separadamente por TR91-0517; regra opcional, formatos além da lista atual e prova de storage produtivo não público permanecem pendentes.',
        'ProtocolAttachmentPolicyService/ProtocolAttachmentService e V12 guardam regras por organização/assunto e validam obrigatoriedade/extensão antes da numeração; ProtocolInteractionService reutiliza extensões configuradas ao aceitar anexos de mensagens. Objetos são entregues sem URL pública. O prazo por assunto é armazenado no catálogo, copiado ao protocolo na abertura e impresso no recibo PDF; TR91-0517 registra seu teste específico.',
        'StaffApp configura regra de anexos e prazo por assunto; portal cidadão envia anexos na abertura e em interações posteriores, acompanha e baixa arquivos; área do servidor lista anexos da abertura e conversa. O cenário de catálogo confere o prazo persistido e no recibo real.',
        'ProtocolVerticalIntegrationTest#protocolAttachmentsAreRequiredValidatedPrivateAndTenantScoped (Testcontainers + S3Mock) verifica regras de abertura, ausência/.exe antes da numeração, upload/download e isolamento; `./dev interaction-test` valida anexos na conversa, negativas tenant/setor, integridade e concorrência. `./dev protocol-deadline-test` e `./dev e2e --grep \'prazo automático no comprovante\'` cobrem o critério específico de TR91-0517. Esses resultados não comprovam todos os formatos.',
        'POC-ANEXOS-PROTOCOLO (parcial): executar ./dev e2e --grep \'assunto exige documento\' para a abertura com PDF obrigatório; executar ./dev e2e --grep \'conversa de protocolo\' para anexos em mensagens com extensão PDF configurada; executar ./dev attachment-test para negativas de abertura, tenant/setor/token. Para prazo e recibo, seguir POC-TR91-0517. Regra opcional, lista completa de formatos, storage produtivo e POC oficial permanecem pendentes.',
        'Evidência parcial: frontend/e2e/protocol-attachments.spec.ts para abertura com PDF obrigatório; `./dev interaction-test` para conversa com PDF configurado, downloads, anexos internos privados, concorrência e conclusão/reabertura; `./dev protocol-deadline-test` e `./dev e2e --grep \'prazo automático no comprovante\'` comprovam separadamente a configuração e impressão do prazo exigidas em TR91-0517. S3Mock não comprova storage produtivo; regra opcional, lista completa de formatos, storage real e POC integral continuam pendentes.',
    ),
}
partial_protocol_completion_requirements = {
    'ED91-0483': (
        'VALIDADO para o critério desta linha: servidor ativo da lotação responsável conclui o protocolo recebido; após conclusão, novas mensagens do requerente e do servidor recebem conflito; somente servidor ativo do setor responsável reabre, e o requerente volta a interagir. O uso atual do token do requerente não implementa participantes adicionais, requisito separado em TR91-0524.',
        'ProtocolService e ProtocolInteractionService serializam transições e gravações pela linha do protocolo; a autorização de conclusão/reabertura deriva da lotação responsável e é validada no backend.',
        'App.vue bloqueia envio externo após conclusão; StaffApp.vue permite reabertura pelo setor responsável. Playwright percorre conclusão, bloqueio externo e interação após reabertura.',
        '`ProtocolVerticalIntegrationTest#protocolInteractionsAreAppendOnlyPrivateAndReopenRequiresResponsibleSector` comprova HTTP 409 para mensagens públicas do requerente e servidor após conclusão, recusa de reabertura por outra lotação e sucesso da reabertura responsável; `applicantInteractionSerializesWithProtocolCompletion` cobre a corrida envio/conclusão. `./dev interaction-test` também passa quatro testes Testcontainers e Playwright 1/1 contra API real.',
        'POC-INTERACOES-PROTOCOLO (ensaio automatizado): abrir protocolo fictício, concluir pelo setor responsável, confirmar HTTP 409 para tentativa de mensagem do requerente e do servidor, tentar reabertura por setor não responsável e depois pelo responsável, e confirmar nova mensagem externa. A ata registra a aprovação da 1DOC na POC do certame; o NexoGov não participou daquela sessão.',
        'Critério literal validado por `./dev interaction-test`: teste PostgreSQL/Testcontainers verifica conclusão, 409 para requerente e servidor, reabertura autorizada/negada e nova mensagem; Playwright 1/1 demonstra o fluxo de navegador. A ata registra aprovação da 1DOC em 03/10/2025; este resultado não é do NexoGov. TR91-0524 é requisito distinto e permanece pendente.',
    ),
    'ED91-0488': (
        'Parcial — requerente, pessoas vinculadas e servidores de setores envolvidos registram interações; anexos seguem formatos configurados e mensagens internas não aparecem no portal cidadão. O fluxo individual de pessoas vinculadas está demonstrado em ensaio automatizado, mas o critério combinado, identidade civil oficial, POC completa, anexos em toda a matriz exigida e operação continuam sem comprovação integral.',
        'ProtocolInteractionService persiste mensagens append-only sob organização/protocolo, autoriza requerente e participante por token individual, setor envolvido por membership, e restringe anexos conforme política e visibilidade. ProtocolParticipantService valida e persiste os campos vinculados ao protocolo; ProtocolPublicAccessService serializa autorização e revogação. Mensagens de outras pessoas são exibidas com autoria neutra.',
        'App.vue permite requerente e pessoa vinculada acompanhar/interagir, mostra convites individuais uma vez e permite revogação pelo requerente; StaffApp.vue mostra o histórico por setor, permite mensagens públicas/internas e administra os campos por assunto. Links provam posse, não identidade civil, e são compartilhados manualmente.',
        'ProtocolVerticalIntegrationTest#protocolInteractionsAreAppendOnlyPrivateAndReopenRequiresResponsibleSector, #applicantInteractionSerializesWithProtocolCompletion, #interactionHistoryUsesWriteOrderWhenAnOlderTransactionWaitsForProtocolLock, #attachmentPolicyChangesWaitForProtocolSubjectReadLock e #protocolParticipantsHaveIndividualScopedAccessAndRevocation. `./dev interaction-test`, `./dev participant-fields-test`, `./dev participant-fields-e2e`, `./dev participant-access-test` e `./dev participant-access-e2e` cobrem camadas distintas; nenhum substitui a POC oficial.',
        'POC-INTERACOES-PROTOCOLO (parcial): demonstrar fluxo do requerente, servidor e uma pessoa vinculada com mensagem pública, manter as notas internas invisíveis ao portal, testar arquivo nas extensões configuradas e concluir/reabrir conforme TR91-0515. Para participantes, executar também o cenário POC-TR91-0524 com dois convites isolados. Credenciais reais de identidade oficial, formatos completos e POC nos 90 minutos prorrogáveis ainda precisam ser demonstrados.',
        'Evidência parcial em docs/INCREMENTO_32.md, docs/INCREMENTO_35.md e docs/INCREMENTO_36.md; Testcontainers e Playwright/API real cobrem interação individual, isolamento entre pessoas/organizações, revogação, limpeza de convites antigos, consultas concorrentes e visibilidade/anexos do fluxo original. O anexo público continua restrito por token e o interno ao servidor autorizado. A POC oficial, identidade civil, política municipal de privacidade e conformidade integral do critério combinado permanecem pendentes.',
    ),
}
partial_protocol_storage_requirements = {
    'ED91-0170': (
        'Parcial — a aplicação não expõe URL direta do objeto e entrega anexos por rotas dinâmicas protegidas por token de acompanhamento ou por autorização de servidor com tenant/setor validados. O teste usa storage simulado e não comprova que hospedagem, bucket e configuração operacionais reais sejam seguros e não públicos; essa evidência contratual permanece pendente.',
        'ProtocolAttachmentStore mantém chaves opacas e ProtocolAttachmentService controla download pelo token ou sessão/autorização; nenhuma URL pública do objeto é devolvida. Configuração e operação de hospedagem produtiva não são comprovadas pelo código/teste local.',
        'Portal e área do servidor solicitam download à API e não recebem link direto do storage. protocol-attachments.spec.ts confirma downloads no navegador por cidadão e servidor; a interface de armazenamento produtivo ainda não foi homologada.',
        'ProtocolVerticalIntegrationTest#protocolAttachmentsAreRequiredValidatedPrivateAndTenantScoped (Testcontainers + Adobe S3Mock) comprova token inválido 404, download autorizado, tenant 404 e setor sem vínculo 403; protocol-attachments.spec.ts confirma download pelos dois perfis. S3Mock não prova segurança de hospedagem real.',
        'POC-ARMAZENAMENTO-ANEXOS (parcial): executar ./dev e2e --grep \'assunto exige documento\' e demonstrar downloads do cidadão e servidor pelos controles da aplicação; executar ./dev attachment-test para negativas de token/tenant/setor. Para aceitar este requisito operacional, anexar evidência do ambiente produtivo de hospedagem não pública; simulação local não a substitui.',
        'Evidência parcial do caminho da aplicação: Playwright protocol-attachments.spec.ts (1/1, 24,1 s) comprova download do cidadão e servidor; teste Testcontainers/S3Mock valida token e isolamento. Não há prova da exposição do servidor/bucket produtivo nem cumprimento da obrigação contratual de hospedagem; sem POC oficial.',
    ),
}
partial_protocol_receipt_requirements = {
    'ED91-0481': (
        'Parcial — o comprovante PDF protegido por token apresenta número e setor inicial logo após a abertura, e o nome do setor permanece estável depois de encaminhamento e despacho. O requisito completo também exige acesso setorial integral ao conteúdo e às movimentações e possibilidade de resposta direta ao requerente; esses comportamentos não são comprovados por esta fatia.',
        'ProtocolService valida o setor ativo no tenant, persiste o ID e o nome inicial em snapshot (V11, sem inferir valor legado) e usa o snapshot no recibo; as transições seguem autorizadas e auditadas pelo servidor.',
        'O portal mostra confirmação e chave após abertura e permite baixar o PDF; o fluxo E2E existente comprova a exibição imediata e o conteúdo do recibo. A resposta direta ao cidadão e o acesso integral do setor continuam pendentes.',
        'ProtocolVerticalIntegrationTest#receiptKeepsTheOpeningDepartmentAfterForwardingAndDispatch, #protocolSubjectCatalogIsTenantScopedOnlineOnlyAndKeepsSnapshots, #forwardingRequiresBodyAndDestinationDepartment e #staffTransitionsRejectProtocolsFromOtherOrganizationsWithoutMutation (PostgreSQL Testcontainers); protocol-subject-catalog.spec.ts valida o PDF real no portal.',
        'POC-ED91-0481 (parcial): abrir solicitação fictícia e conferir imediatamente número, chave e PDF com o setor inicial; encaminhar, receber e despachar, reabrir o PDF e confirmar que o setor inicial não muda. Testar forward/receive/dispatch/complete com organização errada e confirmar 404 sem mudança de estado/histórico. Acesso integral ao conteúdo/movimentações e resposta direta seguem pendentes.',
        'Evidência parcial em docs/INCREMENTO_29.md, teste Testcontainers de comparação do PDF antes/depois, testes de validação 400 e isolamento 404, e protocol-subject-catalog.spec.ts; não comprova acesso documental integral, resposta direta nem POC oficial.',
    ),
}
partial_workflow_requirements = {
    'ED91-0596': (
        'Parcial — a API e a interface autenticada permitem publicar etapas versionadas por assunto, abrir processo com snapshot da definição, pesquisar/listar processos autorizados por tenant/setor com paginação e reabrir o detalhe após recarga, acompanhar etapas/eventos e avançar somente para a etapa seguinte e setor permitido. Permanecem ausentes condições/tempos/grupos configuráveis, encerramento integrado e POC oficial integral.',
        'AdministrativeWorkflowService/Controller, V19/V20 e tabelas tenant-scoped; escopo vem do TenantContext validado. A definição guarda versões imutáveis; processos preservam snapshot de versão e etapas; abertura bloqueia lotação e setor iniciais ativos; avanço bloqueia lotação corrente, setores da etapa e destino ativo em ordem estável, exige a etapa seguinte e setor permitido, e persiste o evento com atualização otimista. Publicação exige expectedVersion e idempotencyKey; o hash usa framing sem ambiguidade, e repetição idêntica retorna a mesma versão sem duplicar definição.',
        'AdministrativeProcessPanel em `/servidor/`, usando o cliente de servidor autenticado e o seletor de organização existente: configura versões por assunto, inicia processo a partir da primeira etapa, pesquisa/lista apenas resumos retornados pela API autorizada, recupera detalhe persistido após nova autenticação e mostra snapshot/linha do tempo do servidor com avanço sequencial.',
        '`./dev process-workflow-test` (AdministrativeWorkflowIntegrationTest, PostgreSQL 16.4 Testcontainers) cobre autorização ADMIN e isolamento; snapshots de nomes/versões; hashing com framing contra colisão de delimitadores; replay idempotente, expectedVersion obsoleta e publicação concorrente; abertura contra revogação/desativação; avanço sequencial, recusa sem mutação, timestamps monotônicos, cross-tenant, revogação e desativação de setor atual/destino; exige membership no setor atribuído mesmo em etapa multi-setor, rejeita setor alternativo e desativação concorrente; 403 antes de revelar estado do destino; contenção concorrente observada em pg_stat_activity; e GET de detalhe aguardando revogação de lotação e negando acesso após o commit sem mutação. `./dev process-list-test` (PostgreSQL Testcontainers) cobre tenant/setor atual, ADMIN organizacional, busca literal, paginação, ordenação estável e resumo sem eventos/etapas. `./dev openapi-check` e `OpenApiContractTest` verificam o contrato. `./dev process-workflow-e2e` (Playwright/API/PostgreSQL descartáveis; `frontend/e2e/process-workflow.spec.ts`) publica v1/v2, preserva o snapshot v1, recusa salto (409) e servidor sem lotação (403) sem mutação, avança sequencialmente, recarrega a página, autentica novamente, busca o processo persistido e abre seu detalhe pela API real.',
        'POC-WORKFLOW-BASICO (parcial): como ADMIN, publicar duas etapas em v1 e abrir processo; publicar v2 alterando os nomes e confirmar que o processo mantém v1; tentar saltar etapa e confirmar HTTP 409 sem evento; tentar avançar sem lotação ativa no setor atual e confirmar HTTP 403 sem mutação; avançar na ordem, recarregar a interface, autenticar novamente, buscar e reabrir o processo. Os testes Testcontainers cobrem isolamento entre organizações e casos concorrentes. Encerramento integrado, condições avançadas, exportações e POC oficial permanecem pendentes.',
        'Evidência parcial: `./dev process-workflow-test`, `./dev process-list-test`, `./dev openapi-check` e `./dev process-workflow-e2e` (cenário Playwright com API/PostgreSQL reais em infraestrutura descartável). A spec `frontend/e2e/process-workflow.spec.ts` registra subfluxos locais: `workflow-publicado-v1`, `workflow-publicado-v2`, `processo-snapshot-v1`, `processo-avancado-para-etapa-final`, `processo-listado-apos-reinicio` e `detalhe-reaberto-da-listagem`. As capturas não provam as recusas HTTP, encerramento, condições avançadas ou POC oficial; manter EM_DESENVOLVIMENTO.',
    ),
}
process_decision_requirements = {
    'ED91-0510': (
        'Configurar, em cada etapa do workflow, quais setores podem registrar uma decisão “Deferido” ou “Indeferido” para o processo; exigir membership ativa do servidor no setor corrente configurado e manter o processo em andamento após a decisão.',
        'AdministrativeWorkflowService/Controller e V22: a permissão por setor é versionada e copiada ao snapshot do processo; TenantContext define a organização; a decisão bloqueia processo, setor e membership ativa exata do setor corrente. Repetir o resultado é idempotente; mudar o resultado acrescenta evento DECIDED sem fechar nem avançar o processo.',
        'AdministrativeProcessPanel em `/servidor/`: ADMIN configura os setores decisores em cada etapa; membro autorizado registra Deferido ou Indeferido; o estado permanece Em andamento e a decisão mais recente aparece no processo e no histórico após recarga.',
        '`./dev process-decision-test` (PostgreSQL Testcontainers) cobre configuração/snapshot, setor não autorizado, ADMIN organizacional sem lotação, membro de setor sem papel ADMIN, os dois resultados, repetição idempotente, mudança append-only, processo ativo e isolamento entre organizações. `./dev process-workflow-test` cobre regressões; `./dev openapi-check` e `OpenApiContractTest` conferem o contrato; `./dev process-workflow-e2e` usa API real no cenário `administrador publica workflow versionado e acompanha o snapshot do processo` em `frontend/e2e/process-workflow.spec.ts` para configuração, decisão e persistência após nova autenticação.',
        'Roteiro local de decisão administrativa: publicar workflow fictício com setor decisor configurado; abrir processo; autenticar com servidor de membership ativa no setor corrente; registrar Deferido ou Indeferido; confirmar decisão no detalhe/lista, histórico e estado Em andamento após recarga. Cobrir o resultado alternativo e acesso negado pelo teste focado. A sessão oficial da POC não foi executada.',
        'Evidência local verificável em `./dev process-decision-test`, `./dev openapi-check`, `./dev process-workflow-e2e` e labels `workflow-decisores-configurados`, `processo-decisao-registrada` e `processo-decisao-persistida`; capturas fictícias mostram configuração, resultado sem encerramento e persistência após recarga. Não são aprovação nem POC oficial.',
    ),
}
for row in rows:
    details = process_decision_requirements.get(row[0])
    if details:
        row[2] = 'Edital PE 91/2025, §14.1.13, fl. impressa 34/80, subtítulo “PROCESSO ADMINISTRATIVO”; Termo de Referência §7, PDF p.53; transcrição conferida visualmente nos PDFs oficiais.'
        row[6:12] = list(details)
        row[12] = 'VALIDADO'
        row[13] = 'CONFERIDA_NO_DOCUMENTO_OFICIAL'

for row in rows:
    details = partial_protocol_attachment_requirements.get(row[0])
    if details:
        row[6:12] = list(details)
        row[12] = 'EM_DESENVOLVIMENTO'

for row in rows:
    details = partial_protocol_completion_requirements.get(row[0])
    if details:
        row[6:12] = list(details)
        row[12] = 'VALIDADO' if row[0] == 'ED91-0483' else 'EM_DESENVOLVIMENTO'

for row in rows:
    details = partial_protocol_storage_requirements.get(row[0])
    if details:
        row[6:12] = list(details)
        row[12] = 'EM_DESENVOLVIMENTO'

for row in rows:
    details = partial_protocol_receipt_requirements.get(row[0])
    if details:
        row[6:12] = list(details)
        row[12] = 'EM_DESENVOLVIMENTO'

for row in rows:
    details = partial_workflow_requirements.get(row[0])
    if details:
        row[6:12] = list(details)
        row[12] = 'EM_DESENVOLVIMENTO'

for row in rows:
    if row[0] not in partial_protocol_access_requirements:
        continue
    row[6] = partial_protocol_access_requirements[row[0]]
    row[7] = 'ProtocolService: autorização por membership ativo e escopo por TenantContext; protocolo bloqueado com SELECT FOR UPDATE nas transições.'
    row[8] = 'Caixa de entrada existente consome a listagem já filtrada pelo servidor; sem mudança de layout nesta fatia.'
    row[9] = 'ProtocolVerticalIntegrationTest#staffProtocolInboxIsLimitedToActiveDepartmentsUnlessOrganizationAdmin, #concurrentCompletionRecordsOnlyOneProtocolTransition e #opensRoutesTracksAndClosesProtocolWithinItsOrganization (Testcontainers/PostgreSQL 16.4).'
    row[10] = 'POC-ACESSO-SETOR: criar protocolo no Setor B; autenticar servidor com lotação apenas no Setor A e confirmar ausência; conceder lotação em B e confirmar presença; validar a administração organizacional; disparar duas conclusões simultâneas e observar um único evento. O roteiro não demonstra todos os níveis de acesso do edital.'
    row[11] = 'Evidência parcial em docs/INCREMENTO_16.md: teste PostgreSQL nega leitura fora de setor, permite lotação ativa e escopo administrativo; concorrência produz uma conclusão e um evento; TRUNCATE do histórico é rejeitado.'
    row[12] = 'EM_DESENVOLVIMENTO'

for row in rows:
    details = partial_protocol_read_status_requirements.get(row[0])
    if details:
        row[4] = 'PROTOCOLO'
        row[6:12] = list(details)
        row[12] = 'EM_DESENVOLVIMENTO'

for row in rows:
    details = partial_protocol_subject_requirements.get(row[0])
    if details:
        row[6:12] = list(details)
        row[12] = 'EM_DESENVOLVIMENTO'

progress_columns = {
    'criterio_de_aceite': 6,
    'backend_envolvido': 7,
    'frontend_envolvido': 8,
    'teste_automatizado': 9,
    'roteiro_poc': 10,
    'evidencia': 11,
    'status': 12,
}
for row in rows:
    previous = previous_progress.get(row[0])
    if previous and previous['requisito_resumo_fiel'] == row[1]:
        regenerated_details = set(partial_protocol_access_requirements) | set(partial_protocol_read_status_requirements) | set(partial_member_access_requirements) | set(partial_protocol_subject_requirements) | set(partial_protocol_attachment_requirements) | set(partial_protocol_completion_requirements) | set(partial_protocol_storage_requirements) | set(partial_protocol_receipt_requirements) | set(partial_workflow_requirements) | set(process_decision_requirements) | set(partial_federated_identity_requirements) | {'ED91-X001', 'ED91-X002', 'ED91-X003', 'ED91-X004', 'ED91-X005', 'ED91-X006'}
        force_source_mapped_refresh = row[0] in partial_protocol_read_status_requirements or row[0] in partial_member_access_requirements or row[0] in partial_protocol_subject_requirements or row[0] in partial_protocol_attachment_requirements or row[0] in partial_protocol_completion_requirements or row[0] in partial_protocol_storage_requirements or row[0] in partial_protocol_receipt_requirements or row[0] in partial_workflow_requirements or row[0] in process_decision_requirements or row[0] in partial_federated_identity_requirements
        preserve_existing = not force_source_mapped_refresh and (
            row[0] not in regenerated_details or previous['status'] != 'NAO_INICIADO'
        )
        if preserve_existing:
            for field, index in progress_columns.items():
                if (
                    field == 'roteiro_poc'
                    and previous.get('modulo') != row[4]
                    and previous.get('roteiro_poc', '').startswith(f"POC-{previous.get('modulo', '')}:")
                ):
                    # Refresh only the generated generic route when its module changes;
                    # preserve any manually maintained POC route.
                    continue
                row[index] = previous[field]

fields=['id','requisito_resumo_fiel','origem_documento_pagina_secao_item','prioridade','modulo','natureza','criterio_de_aceite','backend_envolvido','frontend_envolvido','teste_automatizado','roteiro_poc','evidencia','status','validacao_fonte']
generated_ids={row[0] for row in rows}
for requirement_id,previous in previous_progress.items():
    if requirement_id.startswith(('ED91-','TR91-')) and requirement_id not in generated_ids:
        if requirement_id.startswith('TR91-'):
            rows.append([previous.get(field,'') for field in fields])
            continue
        archived=[previous.get(field,'') for field in fields]
        old_criterion=previous.get('criterio_de_aceite','')
        archived[6]=('BLOQUEADO: este item constava na matriz anterior, mas não foi reconhecido pela extração atual; '
                     'revisar o documento oficial antes de retirar ou substituir o requisito. Critério anterior: '+old_criterion)
        archived[11]=('Preservado da matriz anterior; a extração atual não encontrou a âncora. '
                      'Revisar a fonte e a alteração do parser antes de encerrar o ID.')
        archived[12]='BLOQUEADO'
        rows.append(archived)
id_registry.save()
with out.open('w',encoding='utf-8-sig',newline='') as f:
    w=csv.writer(f,delimiter=';',quoting=csv.QUOTE_MINIMAL)
    w.writerow(fields); w.writerows(rows)
print('requirements',len(rows),'source_bullets',len(items),'modules',len(set(r[4] for r in rows)))
from collections import Counter
print('by module',dict(Counter(r[4] for r in rows)))

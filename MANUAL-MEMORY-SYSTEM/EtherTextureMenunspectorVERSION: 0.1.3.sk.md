# ============================================================
# ETHERCRAFT — EtherTexture Menu Inspector
# VERSION: 0.1.3
#
# MODO:
# MENU TRANSFORMER / DEBUG
#
# OBJETIVO:
# Detectar menus recém-criados e aplicar CMD aos
# ItemStacks daquela instância do inventário.
#
# TESTE ATUAL:
# EXPERIENCE_BOTTLE → CMD 2390
#
# IMPORTANTE:
# - O inventário é tratado como temporário.
# - O plugin pode recriar o menu a qualquer momento.
# - A transformação acontece novamente a cada INVENTORY OPEN.
# - Nenhuma alteração é feita no plugin original.
# ============================================================


on inventory open:

    set {_inventory} to event-inventory


    # ========================================================
    # IDENTIFICAÇÃO GENÉRICA DO INVENTÁRIO
    # ========================================================

    send "" to console
    send "==================================================" to console
    send "[EtherTexture] v0.1.3" to console
    send "[EtherTexture] INVENTORY OPEN DETECTADO" to console
    send "[EtherTexture] Tipo: %type of {_inventory}%" to console
    send "[EtherTexture] Tamanho: %size of {_inventory}%" to console
    send "[EtherTexture] Nome: %name of {_inventory}%" to console
    send "==================================================" to console


    # ========================================================
    # SCANNER
    #
    # Usa o tamanho REAL do inventário.
    # Não assume 54 slots.
    # ========================================================

    loop integers between 0 and (size of {_inventory} - 1):

        set {_slot} to loop-number
        set {_item} to slot {_slot} of {_inventory}


        # ====================================================
        # IGNORAR SLOT SEM ITEM
        # ====================================================

        if {_item} is not set:
            continue

        if {_item} is air:
            continue


        # ====================================================
        # DADOS DO ITEM
        # ====================================================

        set {_material} to type of {_item}
        set {_name} to display name of {_item}


        # ====================================================
        # RELATÓRIO DO ITEM ENCONTRADO
        # ====================================================

        send "[EtherTexture] ITEM ENCONTRADO" to console
        send "[EtherTexture] Slot: %{_slot}%" to console
        send "[EtherTexture] Material: %{_material}%" to console
        send "[EtherTexture] Name: %{_name}%" to console


        # ====================================================
        # TESTE DE CUSTOM MODEL DATA ATUAL
        # ====================================================

        send "[EtherTexture] CMD atual:" to console

        loop custom model data floats of {_item}:

            send "[EtherTexture]   %loop-value%" to console


        # ====================================================
        # TRANSFORMER
        #
        # PRIMEIRO TESTE:
        # somente EXPERIENCE_BOTTLE
        #
        # Não usamos slot.
        # Não usamos nome.
        # Não usamos lore.
        # ====================================================

        if {_material} is experience bottle:

            send "[EtherTexture] >>> ITEM COMPATÍVEL <<<" to console
            send "[EtherTexture] Regra: EXPERIENCE_BOTTLE" to console
            send "[EtherTexture] Aplicando CMD: 2390" to console


            # ================================================
            # APLICA CMD
            # ================================================

            set custom model data floats of {_item} to 2390


            # ================================================
            # DEVOLVE ITEM À INSTÂNCIA ATUAL DO INVENTÁRIO
            # ================================================

            set slot {_slot} of {_inventory} to {_item}


            # ================================================
            # CONFIRMAÇÃO
            # ================================================

            send "[EtherTexture] CMD após alteração:" to console

            loop custom model data floats of {_item}:

                send "[EtherTexture]   %loop-value%" to console

            send "[EtherTexture] TRANSFORMAÇÃO APLICADA." to console

        else:

            send "[EtherTexture] Item ignorado." to console


        send "--------------------------------------------------" to console


    # ========================================================
    # FINAL
    # ========================================================

    send "==================================================" to console
    send "[EtherTexture] v0.1.3 SCAN FINALIZADO" to console
    send "[EtherTexture] Transformações concluídas." to console
    send "==================================================" to console

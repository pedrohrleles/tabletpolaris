package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Uma batida registrada offline, aguardando envio pro Polaris RH assim que a rede voltar. */
@Entity(tableName = "batida_pendente")
data class BatidaPendenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matricula: String,
    val dtHora: String
)

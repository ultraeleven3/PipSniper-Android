fun exportToCsv(context: Context, ledger: TradeLedger): String {
        val trades = getAllTrades(context)
            .filter { it.ledger == ledger }
            .sortedByDescending { it.effectiveTimestamp() }

        val sb = StringBuilder()
        sb.append("Date,Ledger,Symbol,Direction,Session,Status,Lot_Size,Net_USD,Net_Pips,Net_R,Entry,SL,TP,Notes\n")
        for (t in trades) {
            val usd = t.pnlUsd ?: (t.realizedPips() * t.lotSize * 10.0)
            val cleanNotes = t.notes.replace(",", " ").replace("\n", " ")
            sb.append(
                String.format(
                    Locale.US,
                    "%s,%s,%s,%s,%s,%s,%.2f,%.2f,%.1f,%.2f,%.3f,%.3f,%.3f,%s\n",
                    t.dayKey(),
                    t.ledger.key,
                    t.symbol,
                    t.direction,
                    t.session,
                    t.status,
                    t.lotSize,
                    usd,
                    t.realizedPips(),
                    t.realizedR(),
                    t.entry,
                    t.sl,
                    t.tp,
                    cleanNotes
                )
            )
        }
        return sb.toString()
    }

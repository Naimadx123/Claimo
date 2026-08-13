package zone.vao.claimo.storage

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.conversions.Bson
import java.util.UUID

class MongoUsageStorage(
    connectionString: String,
    databaseName: String,
    collectionPrefix: String,
) : UsageStorage {

    private val client: MongoClient = MongoClients.create(connectionString)
    private val global: MongoCollection<Document>
    private val players: MongoCollection<Document>
    private val history: MongoCollection<Document>

    init {
        val database = client.getDatabase(databaseName)
        global = database.getCollection("${collectionPrefix}global_usage")
        players = database.getCollection("${collectionPrefix}player_usage")
        history = database.getCollection("${collectionPrefix}history")
    }

    override fun loadGlobal(): Map<String, Int> {
        val result = HashMap<String, Int>()
        for (doc in global.find()) {
            val id = doc.getString("_id") ?: continue
            result[id] = doc.getInteger("uses", 0)
        }
        return result
    }

    override fun loadPlayer(uuid: UUID): Map<String, Int> {
        val result = HashMap<String, Int>()
        for (doc in players.find(Filters.eq("uuid", uuid.toString()))) {
            val voucherId = doc.getString("voucher_id") ?: continue
            result[voucherId] = doc.getInteger("uses", 0)
        }
        return result
    }

    override fun incrementGlobal(voucherId: String, max: Int): Boolean =
        incrementIfBelow(global, voucherId, max, Document("_id", voucherId).append("uses", 1))

    override fun incrementPlayer(uuid: UUID, voucherId: String, max: Int): Boolean {
        val id = "$uuid:$voucherId"
        val initial = Document("_id", id)
            .append("uuid", uuid.toString())
            .append("voucher_id", voucherId)
            .append("uses", 1)
        return incrementIfBelow(players, id, max, initial)
    }

    override fun decrementGlobal(voucherId: String) {
        decrement(global, voucherId)
    }

    override fun decrementPlayer(uuid: UUID, voucherId: String) {
        decrement(players, "$uuid:$voucherId")
    }

    private fun incrementIfBelow(collection: MongoCollection<Document>, id: String, max: Int, initial: Document): Boolean {
        if (max <= 0) return false
        val belowLimit: Bson = Filters.and(Filters.eq("_id", id), Filters.lt("uses", max))
        repeat(2) {
            if (collection.updateOne(belowLimit, Updates.inc("uses", 1)).matchedCount > 0) return true
            if (collection.find(Filters.eq("_id", id)).limit(1).first() != null) return false
            if (runCatching { collection.insertOne(initial) }.isSuccess) return true
        }
        return false
    }

    private fun decrement(collection: MongoCollection<Document>, id: String) {
        collection.updateOne(
            Filters.and(Filters.eq("_id", id), Filters.gt("uses", 0)),
            Updates.inc("uses", -1),
        )
    }

    override fun recordHistory(entry: RedeemHistoryEntry) {
        history.insertOne(
            Document("voucher_id", entry.voucherId)
                .append("uuid", entry.uuid.toString())
                .append("player", entry.playerName)
                .append("ts", entry.timestamp)
                .append("price", entry.price),
        )
    }

    override fun voucherHistory(voucherId: String, limit: Int): List<RedeemHistoryEntry> =
        queryHistory(Filters.eq("voucher_id", voucherId), limit)

    override fun playerHistory(uuid: UUID, limit: Int): List<RedeemHistoryEntry> =
        queryHistory(Filters.eq("uuid", uuid.toString()), limit)

    override fun uniquePlayers(voucherId: String): Int =
        players.countDocuments(Filters.and(Filters.eq("voucher_id", voucherId), Filters.gt("uses", 0))).toInt()

    private fun queryHistory(filter: Bson, limit: Int): List<RedeemHistoryEntry> =
        history.find(filter).sort(Document("ts", -1)).limit(limit).mapNotNull { doc ->
            runCatching {
                RedeemHistoryEntry(
                    voucherId = doc.getString("voucher_id"),
                    uuid = UUID.fromString(doc.getString("uuid")),
                    playerName = doc.getString("player"),
                    timestamp = doc.getLong("ts"),
                    price = doc.getDouble("price") ?: 0.0,
                )
            }.getOrNull()
        }

    override fun deleteVoucher(voucherId: String) {
        global.deleteOne(Filters.eq("_id", voucherId))
        players.deleteMany(Filters.eq("voucher_id", voucherId))
        history.deleteMany(Filters.eq("voucher_id", voucherId))
    }

    override fun close() {
        client.close()
    }
}

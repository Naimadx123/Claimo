package zone.vao.claimo.storage

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import org.bson.Document
import java.util.UUID

class MongoUsageStorage(
    connectionString: String,
    databaseName: String,
    collectionPrefix: String,
) : UsageStorage {

    private val client: MongoClient = MongoClients.create(connectionString)
    private val global: MongoCollection<Document>
    private val players: MongoCollection<Document>

    init {
        val database = client.getDatabase(databaseName)
        global = database.getCollection("${collectionPrefix}global_usage")
        players = database.getCollection("${collectionPrefix}player_usage")
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

    override fun saveGlobal(voucherId: String, uses: Int) {
        global.updateOne(
            Filters.eq("_id", voucherId),
            Updates.set("uses", uses),
            com.mongodb.client.model.UpdateOptions().upsert(true),
        )
    }

    override fun savePlayer(uuid: UUID, voucherId: String, uses: Int) {
        val id = "$uuid:$voucherId"
        players.replaceOne(
            Filters.eq("_id", id),
            Document("_id", id)
                .append("uuid", uuid.toString())
                .append("voucher_id", voucherId)
                .append("uses", uses),
            ReplaceOptions().upsert(true),
        )
    }

    override fun close() {
        client.close()
    }
}

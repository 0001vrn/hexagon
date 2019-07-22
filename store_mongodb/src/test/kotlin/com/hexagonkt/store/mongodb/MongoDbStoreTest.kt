package com.hexagonkt.store.mongodb

import com.hexagonkt.helpers.error
import com.hexagonkt.settings.SettingsManager
import com.hexagonkt.store.IndexOrder
import com.hexagonkt.store.Store
import org.bson.types.ObjectId
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime

@Test class MongoDbStoreTest {

    private val mongodbUrl = SettingsManager.settings["mongodbUrl"] as? String?
        ?: "mongodb://localhost/test"

    private val store: Store<Company, String> =
        MongoDbStore(Company::class, Company::id, mongodbUrl, "companies")

    private fun createTestEntities() = (0..9)
        .map { ObjectId().toHexString() }
        .map {
            Company(
                id = it,
                foundation = LocalDate.of(2014, 1, 25),
                closeTime = LocalTime.of(11, 42),
                openTime = LocalTime.of(8, 30)..LocalTime.of(14, 36),
                web = URL("http://$it.example.org"),
                people = setOf(
                    Person(name = "John"),
                    Person(name = "Mike")
                )
            )
        }

    @BeforeMethod fun dropCollection() {
        store.drop()
        store.createIndex(true, store.key.name to IndexOrder.ASCENDING)
    }

    @Test fun `New records are stored`() {
        val id = ObjectId().toHexString()
        val company = Company(
            id = id,
            foundation = LocalDate.of(2014, 1, 25),
            closeTime = LocalTime.of(11, 42),
            openTime = LocalTime.of(8, 30)..LocalTime.of(14, 36),
            web = URL("http://example.org"),
            people = setOf(
                Person(name = "John"),
                Person(name = "Mike")
            )
        )

        store.insertOne(company)
        val storedCompany = store.findOne(id)
        assert(storedCompany == company)

        val changedCompany = company.copy(web = URL("http://change.example.org"))
        assert(store.replaceOne(changedCompany))
        val storedModifiedCompany = store.findOne(id)
        assert(storedModifiedCompany == changedCompany)

        val key = changedCompany.id

        assert(store.updateOne(key, "web" to URL("http://update.example.org")))
        assert(store.findOne(key, listOf("web"))["web"] == "http://update.example.org")

        assert(store.updateOne_(key, Company::web to URL("http://update1.example.org")))
        assert(store.findOne(key, listOf("web"))["web"] == "http://update1.example.org")

        assert(store.count() == 1L)

        assert(store.deleteOne(key))

        assert(store.count() == 0L)
    }

    @Test fun `Many records are stored`() {
        val companies = (0..9)
            .map { ObjectId().toHexString() }
            .map {
                Company(
                    id = it,
                    foundation = LocalDate.of(2014, 1, 25),
                    closeTime = LocalTime.of(11, 42),
                    openTime = LocalTime.of(8, 30)..LocalTime.of(14, 36),
                    web = URL("http://$it.example.org"),
                    people = setOf(
                        Person(name = "John"),
                        Person(name = "Mike")
                    )
                )
            }

        val keys = store.insertMany(*companies.toTypedArray())
        assert(store.count() == companies.size.toLong())

        val changedCompanies = companies.map { it.copy(web = URL("http://change.example.org")) }
        assert(store.replaceMany(*changedCompanies.toTypedArray()).size == companies.size)
        assert(store.findAll(listOf("web")).all { it["web"] == "http://change.example.org" })

        // TODO Improve asserts of methods below
        checkFindAllObjects()
        checkFindAllFields()
        checkFindObjects()
        checkFindFields()

        val updateMany = store.updateMany(mapOf("id" to keys), mapOf("web" to "http://update.example.org"))
        assert(updateMany == keys.size.toLong())
        val updatedCompanies = store.findMany(mapOf("id" to keys))
        assert(updatedCompanies.all { it.web.toString() == "http://update.example.org" })

        assert(store.count(mapOf("id" to keys)) == companies.size.toLong())
        assert(store.deleteMany(mapOf("id" to keys)) == keys.size.toLong())
        assert(store.count() == 0L)
    }

    private fun checkFindAllObjects() {
        val results = store.findAll(4, 8, mapOf("id" to false))
        assert(results.size == 2)
        assert(results.all { it.web == URL("http://change.example.org") })

        val results3 = store.findAll(limit = 4, sort = mapOf("id" to false))
        assert(results3.size == 4)
        assert(results3.all { it.web == URL("http://change.example.org") })

        val results4 = store.findAll(skip = 4, sort = mapOf("id" to false))
        assert(results4.size == 6)
        assert(results4.all { it.web == URL("http://change.example.org") })

        val results5 = store.findAll(sort = mapOf("id" to false))
        assert(results5.size == 10)
        assert(results5.all { it.web == URL("http://change.example.org") })
    }

    private fun checkFindAllFields() {
        val fields = listOf("id", "web")

        val results = store.findAll(fields, 4, 8, mapOf("id" to false))
        assert(results.size == 2)
        assert(results.all { it["web"] == "http://change.example.org" })

        val results3 = store.findAll(fields, limit = 4, sort = mapOf("id" to false))
        assert(results3.size == 4)
        assert(results3.all { it["web"] == "http://change.example.org" })

        val results4 = store.findAll(fields, skip = 4, sort = mapOf("id" to false))
        assert(results4.size == 6)
        assert(results4.all { it["web"] == "http://change.example.org" })

        val results5 = store.findAll(fields, sort = mapOf("id" to false))
        assert(results5.size == 10)
        assert(results5.all { it["web"] == "http://change.example.org" })
    }

    private fun checkFindObjects() {
        val results = store.findMany(emptyMap(), 4, 8, mapOf("id" to false))
        assert(results.size == 2)
        assert(results.all { it.web == URL("http://change.example.org") })

        val results3 = store.findMany(emptyMap(), limit = 4, sort = mapOf("id" to false))
        assert(results3.size == 4)
        assert(results3.all { it.web == URL("http://change.example.org") })

        val results4 = store.findMany(emptyMap(), skip = 4, sort = mapOf("id" to false))
        assert(results4.size == 6)
        assert(results4.all { it.web == URL("http://change.example.org") })

        val results5 = store.findMany(emptyMap(), sort = mapOf("id" to false))
        assert(results5.size == 10)
        assert(results5.all { it.web == URL("http://change.example.org") })
    }

    private fun checkFindFields() {
        val fields = listOf("id", "web")

        val results = store.findMany(emptyMap(), fields, 4, 8, mapOf("id" to false))
        assert(results.size == 2)
        assert(results.all { it["web"] == "http://change.example.org" })

        val results3 = store.findMany(emptyMap(), fields, limit = 4, sort = mapOf("id" to false))
        assert(results3.size == 4)
        assert(results3.all { it["web"] == "http://change.example.org" })

        val results4 = store.findMany(emptyMap(), fields, skip = 4, sort = mapOf("id" to false))
        assert(results4.size == 6)
        assert(results4.all { it["web"] == "http://change.example.org" })

        val results5 = store.findMany(emptyMap(), fields, sort = mapOf("id" to false))
        assert(results5.size == 10)
        assert(results5.all { it["web"] == "http://change.example.org" })
    }

    @Test fun `Entities are stored`() {
        val testEntities = createTestEntities()

        val keys = store.saveMany(testEntities)

        val entities = keys.map { store.findOne(it ?: error) }

        assert(entities.map { it?.id }.all { it in keys })

        store.saveMany(testEntities.map { it.copy(web = URL(it.web.toString() + "/modified")) })
    }

    @Test fun `Insert one record returns the proper key`() {
        // TODO Do with MappedClass
        val id = ObjectId().toHexString()
        val mappedClass = Company(
            id = id,
            foundation = LocalDate.of(2014, 1, 25),
            closeTime = LocalTime.of(11, 42),
            openTime = LocalTime.of(8, 30)..LocalTime.of(14, 36),
            web = URL("http://example.org"),
            people = setOf(
                Person(name = "John"),
                Person(name = "Mike")
            )
        )

        val await = store.insertOne(mappedClass)
        val storedClass = store.findOne(await)
        assert(await.isNotBlank())
        assert(mappedClass == storedClass)
    }
}

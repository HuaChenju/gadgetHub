package org.example.order

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@SpringBootTest
class OrderRepositoryTest {
    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @TempDir
    lateinit var directory: Path

    private val items = listOf(OrderItem(1, "Название на момент покупки", 2990, 2, 5980))
    private val details = OrderDetails("+79991234567", null, "pickup", null, "card", false)

    @Test
    fun `orders survive reload and concurrent writes preserve numbering and snapshots`() {
        val file = directory.resolve("orders.json")
        val first = OrderRepository(jsonMapper, file.toString())
        assertEquals(emptyList<Order>(), first.findByUserId(1))
        assertEquals(1L, first.save(1, items, details).number)

        val reloaded = OrderRepository(jsonMapper, file.toString())
        assertEquals(items, reloaded.findByUserId(1).single().items)
        assertEquals(5980L, reloaded.findByUserId(1).single().totalPrice)
        assertEquals(2L, reloaded.save(1, items, details).number)

        val numbers = Executors.newFixedThreadPool(4).use { executor ->
            executor.invokeAll(List(4) { Callable { reloaded.save(2, items, details).number } })
                .map { it.get() }.toSet()
        }
        assertEquals(setOf(3L, 4L, 5L, 6L), numbers)
        val restored = OrderRepository(jsonMapper, file.toString())
        assertEquals(2, restored.findByUserId(1).size)
        assertEquals(4, restored.findByUserId(2).size)
    }

    @Test
    fun `corrupted file is preserved and failed write does not change in-memory orders`() {
        val corrupt = directory.resolve("corrupt.json")
        Files.writeString(corrupt, "{")
        assertThrows(IllegalStateException::class.java) { OrderRepository(jsonMapper, corrupt.toString()) }
        assertEquals("{", Files.readString(corrupt))

        val file = directory.resolve("orders.json")
        val repository = OrderRepository(jsonMapper, file.toString())
        repository.save(1, items, details)
        val saved = Files.readAllBytes(file)
        Files.delete(file)
        Files.createDirectory(file)
        assertThrows(IOException::class.java) { repository.save(1, items, details) }
        assertEquals(1, repository.findByUserId(1).size)

        Files.delete(file)
        Files.write(file, saved)
        assertEquals(1, OrderRepository(jsonMapper, file.toString()).findByUserId(1).size)
        assertEquals(2L, repository.save(1, items, details).number)
    }
}

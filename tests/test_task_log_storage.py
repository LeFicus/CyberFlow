"""Regression coverage for append-only crawler task log persistence."""

from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "crawler-consumer"))

from db.repository import CursorRepository, TASK_LOG_CHUNK_CHARS


class AsyncContext:
    def __init__(self, value):
        self.value = value

    async def __aenter__(self):
        return self.value

    async def __aexit__(self, *_):
        return False


class RecordingCursor:
    def __init__(self):
        self.executed = []
        self.batches = []

    def __aenter__(self):
        return self

    async def __aexit__(self, *_):
        return False

    async def execute(self, sql, params):
        self.executed.append((" ".join(sql.split()), params))

    async def executemany(self, sql, params):
        self.batches.append((" ".join(sql.split()), list(params)))


class FakeConnection:
    def __init__(self, cursor):
        self._cursor = cursor

    def cursor(self):
        return AsyncContext(self._cursor)


class FakePool:
    def __init__(self, cursor):
        self._connection = FakeConnection(cursor)

    def acquire(self):
        return AsyncContext(self._connection)


class TaskLogStorageTests(unittest.IsolatedAsyncioTestCase):
    async def test_append_splits_large_messages_into_bounded_insert_only_chunks(self):
        cursor = RecordingCursor()
        repository = CursorRepository()
        repository.pool = FakePool(cursor)
        content = "🚀" * TASK_LOG_CHUNK_CHARS + "x" * (TASK_LOG_CHUNK_CHARS + 1)

        await repository.append_task_log("task-1", content)

        self.assertEqual(len(cursor.batches), 1)
        sql, rows = cursor.batches[0]
        self.assertIn("INSERT INTO task_crawl_log", sql)
        self.assertNotIn("CONCAT", sql.upper())
        self.assertEqual([len(row[0]) for row in rows],
                         [TASK_LOG_CHUNK_CHARS, TASK_LOG_CHUNK_CHARS, 1])
        self.assertEqual([row[1] for row in rows],
                         [TASK_LOG_CHUNK_CHARS, TASK_LOG_CHUNK_CHARS, 1])
        self.assertTrue(all(row[2] == "task-1" for row in rows))

    async def test_reset_deletes_chunks_and_clears_only_the_legacy_payload(self):
        cursor = RecordingCursor()
        repository = CursorRepository()
        repository.pool = FakePool(cursor)

        await repository.reset_task_log("task-2")

        self.assertEqual(len(cursor.executed), 2)
        self.assertIn("DELETE FROM task_crawl_log", cursor.executed[0][0])
        self.assertIn("SET crawl_log=NULL", cursor.executed[1][0])


class TaskLogMigrationTests(unittest.TestCase):
    def test_log_table_inherits_database_collation_for_foreign_key_compatibility(self):
        migration = (
            ROOT / "script" / "migrations" / "20260901_split_task_crawl_logs.sql"
        ).read_text(encoding="utf-8")
        create_table = migration.split(";", 1)[0]

        self.assertIn("FOREIGN KEY (task_id) REFERENCES task_history(task_id)", create_table)
        self.assertNotIn("COLLATE=", create_table.upper())
        self.assertNotIn("COLLATE ", create_table.upper())


if __name__ == "__main__":
    unittest.main()

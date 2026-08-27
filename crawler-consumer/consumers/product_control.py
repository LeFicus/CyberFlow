"""A single active-time deadline and portable control of the owned child process."""

import time
import psutil


class ActiveDeadline:
    def __init__(self, seconds, clock=time.monotonic):
        self.clock = clock
        self.deadline = clock() + seconds
        self.paused_at = None

    def pause(self):
        if self.paused_at is None:
            self.paused_at = self.clock()

    def resume(self):
        if self.paused_at is not None:
            self.deadline += self.clock() - self.paused_at
            self.paused_at = None

    def remaining(self):
        return max(0.0, self.deadline - (self.paused_at if self.paused_at is not None else self.clock()))

    def expired(self):
        return self.paused_at is None and self.remaining() <= 0


def pause_child(process):
    if process.returncode is None:
        try:
            psutil.Process(process.pid).suspend()
        except psutil.NoSuchProcess:
            pass


def resume_child(process):
    if process.returncode is None:
        try:
            psutil.Process(process.pid).resume()
        except psutil.NoSuchProcess:
            pass

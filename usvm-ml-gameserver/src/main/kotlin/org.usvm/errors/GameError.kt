package org.usvm.errors

abstract class GameError(msg: String) : HandledError(msg)
class NotStartedYetError : GameError("Can't accept a new step: a game haven't started yet")
class AlreadyInGameError : GameError("Can't run a new map: you need to finish a running game")
class InterruptedError : GameError("Game was interrupted while playing")

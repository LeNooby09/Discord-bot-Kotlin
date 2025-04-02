package commands

/**
 * Easter egg command that responds with a special message when a specific prefix is set.
 * This command only works if the server's prefix is set to a specific value.
 */
class MinecraftMovieCommand : EasterEggCommand() {
	override val name = "movie"
	override val description = "A mysterious command"

	// The special prefix that activates this easter egg
	override val easterEggPrefix = "minecraft"

	override suspend fun getEasterEggMessage(): String {
		return quotes()
	}

	private fun quotes(): String {
		val quotes = mutableListOf(
			"I.. am STEVE",
			"Chicken jockey!",
			"FLINT AND STEEL!",
			"The Nether",
			"**RELEASE**",
			"This is a crafting table",
			"Welcome to the Overworld",
			"As a child I yearned for the mines"
		)
		return quotes.random()
	}
}

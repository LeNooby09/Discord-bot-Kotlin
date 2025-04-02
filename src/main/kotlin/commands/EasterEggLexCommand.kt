package commands

/**
 * Easter egg command that responds with a special message when a specific prefix is set.
 * This command only works if the server's prefix is set to a specific value.
 */
class EasterEggLexCommand : EasterEggCommand() {
	override val name = "lex"
	override val description = "A mysterious command"

	// The special prefix that activates this easter egg
	override val easterEggPrefix = "sesbian"

	override suspend fun getEasterEggMessage(): String {
		return "here's some sesbian lex for the degenerates amongst us :3c\n|| https://bsky.app/profile/alicepp.bsky.social/post/3lllpbjfg4k2x ||"
	}
}

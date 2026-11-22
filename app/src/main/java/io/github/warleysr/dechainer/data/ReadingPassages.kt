package io.github.warleysr.dechainer.data

import android.content.Context
import io.github.warleysr.dechainer.security.SecurityManager

/** One text shown by the reading challenge, already in the language it will be displayed in. */
data class ReadingPassage(val text: String, val source: String)

/** A built-in passage, carried in every language the app ships with. Arabic is optional. */
class BuiltInPassage(
    private val textEn: String,
    private val sourceEn: String,
    private val textPt: String,
    private val sourcePt: String,
    private val textAr: String? = null,
    private val sourceAr: String? = null
) {
    fun resolve(language: SecurityManager.ReadingLanguage) = when (language) {
        SecurityManager.ReadingLanguage.ARABIC ->
            if (textAr != null) ReadingPassage(textAr, sourceAr ?: sourceEn)
            else ReadingPassage(textEn, sourceEn)

        SecurityManager.ReadingLanguage.PORTUGUESE -> ReadingPassage(textPt, sourcePt)
        SecurityManager.ReadingLanguage.ENGLISH -> ReadingPassage(textEn, sourceEn)
    }
}

/**
 * Texts for the reading challenge. Nothing here is paraphrased from memory: the verses were taken
 * from published translations and the quotes checked against the source works.
 *
 * - Bible: King James Version (English) and João Ferreira de Almeida (Portuguese), both public domain.
 * - Quran: Saheeh International (English) and Samir El-Hayek (Portuguese).
 * - Quotes: the English follows public-domain translations of each work (Jowett for Plato, Dakyns
 *   for Xenophon, Chase for Aristotle, Aubrey Stewart for Seneca, George Long for Epictetus,
 *   Casaubon for Marcus Aurelius, Pusey for Augustine, Trotter for Pascal, Max Müller for the
 *   Dhammapada, Garnett for Dostoevsky); the Portuguese is a faithful rendering of the same passage.
 */
object ReadingLibrary {

    fun passages(context: Context, source: SecurityManager.ReadingSource, language: SecurityManager.ReadingLanguage): List<ReadingPassage> =
        when (source) {
            SecurityManager.ReadingSource.BIBLE -> BIBLE.map { it.resolve(language) }
            SecurityManager.ReadingSource.QURAN -> QURAN.map { it.resolve(language) }
            SecurityManager.ReadingSource.QUOTES -> QUOTES.map { it.resolve(language) }
            SecurityManager.ReadingSource.CUSTOM -> SecurityManager.getCustomReadingPassages(context)
        }

    fun availableCount(context: Context, source: SecurityManager.ReadingSource): Int =
        when (source) {
            SecurityManager.ReadingSource.BIBLE -> BIBLE.size
            SecurityManager.ReadingSource.QURAN -> QURAN.size
            SecurityManager.ReadingSource.QUOTES -> QUOTES.size
            SecurityManager.ReadingSource.CUSTOM -> SecurityManager.getCustomReadingPassages(context).size
        }

    /** A random selection of [SecurityManager.getReadingCount] passages from the configured source. */
    fun pick(context: Context, language: SecurityManager.ReadingLanguage): List<ReadingPassage> {
        val all = passages(context, SecurityManager.getReadingSource(context), language)
        return all.shuffled().take(SecurityManager.getReadingCount(context).coerceAtMost(all.size))
    }

    private val BIBLE = listOf(
        BuiltInPassage(
            "I made a covenant with mine eyes; why then should I think upon a maid?",
            "Job 31:1",
            "Fiz pacto com os meus olhos; como, pois, os fixaria numa virgem?",
            "Jó 31:1"
        ),
        BuiltInPassage(
            "Wherewithal shall a young man cleanse his way? by taking heed thereto according to thy word.",
            "Psalm 119:9",
            "Como purificará o jovem o seu caminho? Observando-o de acordo com a tua palavra.",
            "Salmos 119:9"
        ),
        BuiltInPassage(
            "Turn away mine eyes from beholding vanity; and quicken thou me in thy way.",
            "Psalm 119:37",
            "Desvia os meus olhos de contemplarem a vaidade, e vivifica-me no teu caminho.",
            "Salmos 119:37"
        ),
        BuiltInPassage(
            "Keep thy heart with all diligence; for out of it are the issues of life.",
            "Proverbs 4:23",
            "Guarda com toda a diligência o teu coração, porque dele procedem as fontes da vida.",
            "Provérbios 4:23"
        ),
        BuiltInPassage(
            "Lust not after her beauty in thine heart; neither let her take thee with her eyelids.",
            "Proverbs 6:25",
            "Não cobices no teu coração a sua formosura, nem te deixes prender pelos seus olhares.",
            "Provérbios 6:25"
        ),
        BuiltInPassage(
            "But whoso committeth adultery with a woman lacketh understanding: he that doeth it destroyeth his own soul.",
            "Proverbs 6:32",
            "O que adultera com uma mulher é falto de entendimento; destrói-se a si mesmo, quem assim procede.",
            "Provérbios 6:32"
        ),
        BuiltInPassage(
            "Blessed are the pure in heart: for they shall see God.",
            "Matthew 5:8",
            "Bem-aventurados os limpos de coração, porque eles verão a Deus.",
            "Mateus 5:8"
        ),
        BuiltInPassage(
            "But I say unto you, That whosoever looketh on a woman to lust after her hath committed adultery with her already in his heart.",
            "Matthew 5:28",
            "Eu, porém, vos digo que todo aquele que olhar para uma mulher para a cobiçar, já em seu coração cometeu adultério com ela.",
            "Mateus 5:28"
        ),
        BuiltInPassage(
            "Watch and pray, that ye enter not into temptation: the spirit indeed is willing, but the flesh is weak.",
            "Matthew 26:41",
            "Vigiai e orai, para que não entreis em tentação; o espírito, na verdade, está pronto, mas a carne é fraca.",
            "Mateus 26:41"
        ),
        BuiltInPassage(
            "And be not conformed to this world: but be ye transformed by the renewing of your mind, that ye may prove what is that good, and acceptable, and perfect, will of God.",
            "Romans 12:2",
            "E não vos conformeis a este mundo, mas transformai-vos pela renovação da vossa mente, para que experimenteis qual seja a boa, agradável, e perfeita vontade de Deus.",
            "Romanos 12:2"
        ),
        BuiltInPassage(
            "But put ye on the Lord Jesus Christ, and make not provision for the flesh, to fulfil the lusts thereof.",
            "Romans 13:14",
            "Mas revesti-vos do Senhor Jesus Cristo; e não tenhais cuidado da carne em suas concupiscências.",
            "Romanos 13:14"
        ),
        BuiltInPassage(
            "Flee fornication. Every sin that a man doeth is without the body; but he that committeth fornication sinneth against his own body.",
            "1 Corinthians 6:18",
            "Fugi da prostituição. Qualquer outro pecado que o homem comete, é fora do corpo; mas o que se prostitui peca contra o seu próprio corpo.",
            "1 Coríntios 6:18"
        ),
        BuiltInPassage(
            "What? know ye not that your body is the temple of the Holy Ghost which is in you, which ye have of God, and ye are not your own? For ye are bought with a price: therefore glorify God in your body, and in your spirit, which are God’s.",
            "1 Corinthians 6:19-20",
            "Ou não sabeis que o vosso corpo é santuário do Espírito Santo, que habita em vós, o qual possuís da parte de Deus, e que não sois de vós mesmos? Porque fostes comprados por preço; glorificai pois a Deus no vosso corpo.",
            "1 Coríntios 6:19-20"
        ),
        BuiltInPassage(
            "There hath no temptation taken you but such as is common to man: but God is faithful, who will not suffer you to be tempted above that ye are able; but will with the temptation also make a way to escape, that ye may be able to bear it.",
            "1 Corinthians 10:13",
            "Não vos sobreveio nenhuma tentação, senão humana; mas fiel é Deus, o qual não deixará que sejais tentados acima do que podeis resistir, antes com a tentação dará também o meio de saída, para que a possais suportar.",
            "1 Coríntios 10:13"
        ),
        BuiltInPassage(
            "This I say then, Walk in the Spirit, and ye shall not fulfil the lust of the flesh.",
            "Galatians 5:16",
            "Digo, porém: Andai pelo Espírito, e não haveis de cumprir a cobiça da carne.",
            "Gálatas 5:16"
        ),
        BuiltInPassage(
            "For this is the will of God, even your sanctification, that ye should abstain from fornication: That every one of you should know how to possess his vessel in sanctification and honour; Not in the lust of concupiscence, even as the Gentiles which know not God.",
            "1 Thessalonians 4:3-5",
            "Porque esta é a vontade de Deus, a saber, a vossa santificação: que vos abstenhais da prostituição, que cada um de vós saiba possuir o seu vaso em santidade e honra, não na paixão da concupiscência, como os gentios que não conhecem a Deus.",
            "1 Tessalonicenses 4:3-5"
        ),
        BuiltInPassage(
            "Flee also youthful lusts: but follow righteousness, faith, charity, peace, with them that call on the Lord out of a pure heart.",
            "2 Timothy 2:22",
            "Foge também das paixões da mocidade, e segue a justiça, a fé, o amor, a paz com os que, de coração puro, invocam o Senhor.",
            "2 Timóteo 2:22"
        ),
        BuiltInPassage(
            "Marriage is honourable in all, and the bed undefiled: but whoremongers and adulterers God will judge.",
            "Hebrews 13:4",
            "Honrado seja entre todos o matrimônio e o leito sem mácula; pois aos devassos e adúlteros, Deus os julgará.",
            "Hebreus 13:4"
        ),
        BuiltInPassage(
            "But every man is tempted, when he is drawn away of his own lust, and enticed. Then when lust hath conceived, it bringeth forth sin: and sin, when it is finished, bringeth forth death.",
            "James 1:14-15",
            "Cada um, porém, é tentado, quando atraído e engodado pela sua própria concupiscência; então a concupiscência, havendo concebido, dá à luz o pecado; e o pecado, sendo consumado, gera a morte.",
            "Tiago 1:14-15"
        ),
        BuiltInPassage(
            "Dearly beloved, I beseech you as strangers and pilgrims, abstain from fleshly lusts, which war against the soul.",
            "1 Peter 2:11",
            "Amados, exorto-vos, como a peregrinos e forasteiros, que vos abstenhais das concupiscências da carne, as quais combatem contra a alma.",
            "1 Pedro 2:11"
        )
    )

    private val QURAN = listOf(
        BuiltInPassage(
            "Satan threatens you with poverty and orders you to immorality, while Allah promises you forgiveness from Him and bounty. And Allah is all-Encompassing and Knowing.",
            "Al-Baqarah 2:268",
            "Satanás vos atemoriza com a miséria e vos induz à obscenidade; por outro lado, Deus vos promete a Sua indulgência e a Sua graça, porque é Munificente, Sapientíssimo.",
            "Al-Baqara 2:268",
            "ٱلشَّيْطَٰنُ يَعِدُكُمُ ٱلْفَقْرَ وَيَأْمُرُكُم بِٱلْفَحْشَآءِ ۖ وَٱللَّهُ يَعِدُكُم مَّغْفِرَةًۭ مِّنْهُ وَفَضْلًۭا ۗ وَٱللَّهُ وَٰسِعٌ عَلِيمٌۭ",
            "البقرة 2:268"
        ),
        BuiltInPassage(
            "Beautified for people is the love of that which they desire - of women and sons, heaped-up sums of gold and silver, fine branded horses, and cattle and tilled land. That is the enjoyment of worldly life, but Allah has with Him the best return.",
            "Ali 'Imran 3:14",
            "Aos homens foi abrilhantado o amor à concupiscência relacionada às mulheres, aos filhos, ao entesouramento do ouro e da prata, aos cavalos de raça, ao gado e às sementeiras. Tal é o gozo da vida terrena; porém, a bem-aventurança está ao lado de Deus.",
            "Al Imran 3:14",
            "زُيِّنَ لِلنَّاسِ حُبُّ ٱلشَّهَوَٰتِ مِنَ ٱلنِّسَآءِ وَٱلْبَنِينَ وَٱلْقَنَٰطِيرِ ٱلْمُقَنطَرَةِ مِنَ ٱلذَّهَبِ وَٱلْفِضَّةِ وَٱلْخَيْلِ ٱلْمُسَوَّمَةِ وَٱلْأَنْعَٰمِ وَٱلْحَرْثِ ۗ ذَٰلِكَ مَتَٰعُ ٱلْحَيَوٰةِ ٱلدُّنْيَا ۖ وَٱللَّهُ عِندَهُۥ حُسْنُ ٱلْمَـَٔابِ",
            "آل عمۡران 3:14"
        ),
        BuiltInPassage(
            "And those who, when they commit an immorality or wrong themselves [by transgression], remember Allah and seek forgiveness for their sins - and who can forgive sins except Allah? - and [who] do not persist in what they have done while they know.",
            "Ali 'Imran 3:135",
            "Que, quando cometem uma obscenidade ou se condenam, mencionam a Deus e imploram o perdão por seus pecados - mas quem, senão Deus perdoa os pecados? - e não reincidem, com conhecimento, no que cometeram.",
            "Al Imran 3:135",
            "وَٱلَّذِينَ إِذَا فَعَلُوا۟ فَٰحِشَةً أَوْ ظَلَمُوٓا۟ أَنفُسَهُمْ ذَكَرُوا۟ ٱللَّهَ فَٱسْتَغْفَرُوا۟ لِذُنُوبِهِمْ وَمَن يَغْفِرُ ٱلذُّنُوبَ إِلَّا ٱللَّهُ وَلَمْ يُصِرُّوا۟ عَلَىٰ مَا فَعَلُوا۟ وَهُمْ يَعْلَمُونَ",
            "آل عمۡران 3:135"
        ),
        BuiltInPassage(
            "Allah wants to accept your repentance, but those who follow [their] passions want you to digress [into] a great deviation. And Allah wants to lighten for you [your difficulties]; and mankind was created weak.",
            "An-Nisa 4:27-28",
            "Deus deseja absolver-vos; porém, os que seguem os desejos vãos anseiam vos desviar profundamente. E Deus deseja aliviar-vos o fardo, porque o homem foi criado débil.",
            "An-Nissá 4:27-28",
            "وَٱللَّهُ يُرِيدُ أَن يَتُوبَ عَلَيْكُمْ وَيُرِيدُ ٱلَّذِينَ يَتَّبِعُونَ ٱلشَّهَوَٰتِ أَن تَمِيلُوا۟ مَيْلًا عَظِيمًۭا يُرِيدُ ٱللَّهُ أَن يُخَفِّفَ عَنكُمْ ۚ وَخُلِقَ ٱلْإِنسَٰنُ ضَعِيفًۭا",
            "النساء 4:27-28"
        ),
        BuiltInPassage(
            "Say, \"My Lord has only forbidden immoralities - what is apparent of them and what is concealed - and sin, and oppression without right, and that you associate with Allah that for which He has not sent down authority, and that you say about Allah that which you do not know.\"",
            "Al-A'raf 7:33",
            "Dize: Meu Senhor vedou as obscenidades, manifestas ou íntimas; o delito; a agressão injusta; o atribuir parceiros a Ele, porque jamais deu autoridade a que digais d'Ele o que ignorais.",
            "Al A'raf 7:33",
            "قُلْ إِنَّمَا حَرَّمَ رَبِّىَ ٱلْفَوَٰحِشَ مَا ظَهَرَ مِنْهَا وَمَا بَطَنَ وَٱلْإِثْمَ وَٱلْبَغْىَ بِغَيْرِ ٱلْحَقِّ وَأَن تُشْرِكُوا۟ بِٱللَّهِ مَا لَمْ يُنَزِّلْ بِهِۦ سُلْطَٰنًۭا وَأَن تَقُولُوا۟ عَلَى ٱللَّهِ مَا لَا تَعْلَمُونَ",
            "الأعۡراف 7:33"
        ),
        BuiltInPassage(
            "And she, in whose house he was, sought to seduce him. She closed the doors and said, \"Come, you.\" He said, \"[I seek] the refuge of Allah. Indeed, he is my master, who has made good my residence. Indeed, wrongdoers will not succeed.\"",
            "Yusuf 12:23",
            "A mulher, em cuja casa se alojara, tentou seduzi-lo; fechou as portas e lhe disse: Agora vem! Porém, ele disse: Amparo-me em Deus! Ele (o marido) é meu amo e acolheu-me condignamente. Em verdade, os iníquos jamais prosperarão.",
            "Yussuf 12:23",
            "وَرَٰوَدَتْهُ ٱلَّتِى هُوَ فِى بَيْتِهَا عَن نَّفْسِهِۦ وَغَلَّقَتِ ٱلْأَبْوَٰبَ وَقَالَتْ هَيْتَ لَكَ ۚ قَالَ مَعَاذَ ٱللَّهِ ۖ إِنَّهُۥ رَبِّىٓ أَحْسَنَ مَثْوَاىَ ۖ إِنَّهُۥ لَا يُفْلِحُ ٱلظَّٰلِمُونَ",
            "يوسف 12:23"
        ),
        BuiltInPassage(
            "He said, \"My Lord, prison is more to my liking than that to which they invite me. And if You do not avert from me their plan, I might incline toward them and [thus] be of the ignorant.\"",
            "Yusuf 12:33",
            "Disse (José): Ó Senhor meu, é preferível o cárcere ao que me incitam; porém, se não afastares de mim as suas conspirações, cederei a elas e serei um dos néscios.",
            "Yussuf 12:33",
            "قَالَ رَبِّ ٱلسِّجْنُ أَحَبُّ إِلَىَّ مِمَّا يَدْعُونَنِىٓ إِلَيْهِ ۖ وَإِلَّا تَصْرِفْ عَنِّى كَيْدَهُنَّ أَصْبُ إِلَيْهِنَّ وَأَكُن مِّنَ ٱلْجَٰهِلِينَ",
            "يوسف 12:33"
        ),
        BuiltInPassage(
            "And I do not acquit myself. Indeed, the soul is a persistent enjoiner of evil, except those upon which my Lord has mercy. Indeed, my Lord is Forgiving and Merciful.",
            "Yusuf 12:53",
            "Porém, eu não me escuso, porquanto o ser é propenso ao mal, exceto aqueles de quem o meu Senhor se apiada, porque o meu Senhor é Indulgente, Misericordiosíssimo.",
            "Yussuf 12:53",
            "۞ وَمَآ أُبَرِّئُ نَفْسِىٓ ۚ إِنَّ ٱلنَّفْسَ لَأَمَّارَةٌۢ بِٱلسُّوٓءِ إِلَّا مَا رَحِمَ رَبِّىٓ ۚ إِنَّ رَبِّى غَفُورٌۭ رَّحِيمٌۭ",
            "يوسف 12:53"
        ),
        BuiltInPassage(
            "Indeed, Allah orders justice and good conduct and giving to relatives and forbids immorality and bad conduct and oppression. He admonishes you that perhaps you will be reminded.",
            "An-Nahl 16:90",
            "Deus ordena a justiça, a caridade, o auxílio aos parentes, e veda a obscenidade, o ilícito e a iniqüidade. Ele vos exorta a que mediteis.",
            "An Nahl 16:90",
            "۞ إِنَّ ٱللَّهَ يَأْمُرُ بِٱلْعَدْلِ وَٱلْإِحْسَٰنِ وَإِيتَآئِ ذِى ٱلْقُرْبَىٰ وَيَنْهَىٰ عَنِ ٱلْفَحْشَآءِ وَٱلْمُنكَرِ وَٱلْبَغْىِ ۚ يَعِظُكُمْ لَعَلَّكُمْ تَذَكَّرُونَ",
            "النحۡل 16:90"
        ),
        BuiltInPassage(
            "And do not approach unlawful sexual intercourse. Indeed, it is ever an immorality and is evil as a way.",
            "Al-Isra 17:32",
            "Evitai a fornicação, porque é uma obscenidade e um péssimo exemplo!",
            "Al Isrá 17:32",
            "وَلَا تَقْرَبُوا۟ ٱلزِّنَىٰٓ ۖ إِنَّهُۥ كَانَ فَٰحِشَةًۭ وَسَآءَ سَبِيلًۭا",
            "الإسۡراء 17:32"
        ),
        BuiltInPassage(
            "And do not pursue that of which you have no knowledge. Indeed, the hearing, the sight and the heart - about all those [one] will be questioned.",
            "Al-Isra 17:36",
            "Não sigas (ó humano) o que ignoras, porque pelo teu ouvido, pela tua vista, e pelo teu coração, por tudo isto será responsável!",
            "Al Isrá 17:36",
            "وَلَا تَقْفُ مَا لَيْسَ لَكَ بِهِۦ عِلْمٌ ۚ إِنَّ ٱلسَّمْعَ وَٱلْبَصَرَ وَٱلْفُؤَادَ كُلُّ أُو۟لَٰٓئِكَ كَانَ عَنْهُ مَسْـُٔولًۭا",
            "الإسۡراء 17:36"
        ),
        BuiltInPassage(
            "O you who have believed, do not follow the footsteps of Satan. And whoever follows the footsteps of Satan - indeed, he enjoins immorality and wrongdoing. And if not for the favor of Allah upon you and His mercy, not one of you would have been pure, ever, but Allah purifies whom He wills, and Allah is Hearing and Knowing.",
            "An-Nur 24:21",
            "Ó fiéis, não sigais as pegadas de Satanás; e saiba, quem segue as pegadas de Satanás, que ele recomenda a obscenidade e o ilícito. E se não fosse pela graça de Deus e pela Sua misericórdia para convosco, Ele jamais teria purificado nenhum de vós; porém, Deus purifica quem Lhe apraz, porque é Oniouvinte, Sapientíssimo.",
            "An Nur 24:21",
            "۞ يَٰٓأَيُّهَا ٱلَّذِينَ ءَامَنُوا۟ لَا تَتَّبِعُوا۟ خُطُوَٰتِ ٱلشَّيْطَٰنِ ۚ وَمَن يَتَّبِعْ خُطُوَٰتِ ٱلشَّيْطَٰنِ فَإِنَّهُۥ يَأْمُرُ بِٱلْفَحْشَآءِ وَٱلْمُنكَرِ ۚ وَلَوْلَا فَضْلُ ٱللَّهِ عَلَيْكُمْ وَرَحْمَتُهُۥ مَا زَكَىٰ مِنكُم مِّنْ أَحَدٍ أَبَدًۭا وَلَٰكِنَّ ٱللَّهَ يُزَكِّى مَن يَشَآءُ ۗ وَٱللَّهُ سَمِيعٌ عَلِيمٌۭ",
            "النور 24:21"
        ),
        BuiltInPassage(
            "Tell the believing men to reduce [some] of their vision and guard their private parts. That is purer for them. Indeed, Allah is Acquainted with what they do.",
            "An-Nur 24:30",
            "Dize aos fiéis que recatem os seus olhares e conservem seus pudores, porque isso é mais benéfico para eles; Deus está bem inteirado de tudo quanto fazem.",
            "An Nur 24:30",
            "قُل لِّلْمُؤْمِنِينَ يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ وَيَحْفَظُوا۟ فُرُوجَهُمْ ۚ ذَٰلِكَ أَزْكَىٰ لَهُمْ ۗ إِنَّ ٱللَّهَ خَبِيرٌۢ بِمَا يَصْنَعُونَ",
            "النور 24:30"
        ),
        BuiltInPassage(
            "Recite, [O Muhammad], what has been revealed to you of the Book and establish prayer. Indeed, prayer prohibits immorality and wrongdoing, and the remembrance of Allah is greater. And Allah knows that which you do.",
            "Al-'Ankabut 29:45",
            "Recita o que te foi revelado do Livro e observa a oração, porque a oração preserva (o homem) da obscenidade e do ilícito; mas, na verdade, a recordação de Deus é o mais importante. Sabei que Deus está ciente de tudo quanto fazeis.",
            "Al Ankabut 29:45",
            "ٱتْلُ مَآ أُوحِىَ إِلَيْكَ مِنَ ٱلْكِتَٰبِ وَأَقِمِ ٱلصَّلَوٰةَ ۖ إِنَّ ٱلصَّلَوٰةَ تَنْهَىٰ عَنِ ٱلْفَحْشَآءِ وَٱلْمُنكَرِ ۗ وَلَذِكْرُ ٱللَّهِ أَكْبَرُ ۗ وَٱللَّهُ يَعْلَمُ مَا تَصْنَعُونَ",
            "العنكبوت 29:45"
        ),
        BuiltInPassage(
            "Say, \"O My servants who have transgressed against themselves [by sinning], do not despair of the mercy of Allah. Indeed, Allah forgives all sins. Indeed, it is He who is the Forgiving, the Merciful.\"",
            "Az-Zumar 39:53",
            "Dize: Ó servos meus, que se excederam contra si próprios, não desespereis da misericórdia de Deus; certamente, Ele perdoa todos os pecados, porque Ele é o Indulgente, o Misericordiosíssimo.",
            "Az Zumar 39:53",
            "۞ قُلْ يَٰعِبَادِىَ ٱلَّذِينَ أَسْرَفُوا۟ عَلَىٰٓ أَنفُسِهِمْ لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ ۚ إِنَّ ٱللَّهَ يَغْفِرُ ٱلذُّنُوبَ جَمِيعًا ۚ إِنَّهُۥ هُوَ ٱلْغَفُورُ ٱلرَّحِيمُ",
            "الزمر 39:53"
        ),
        BuiltInPassage(
            "He knows that which deceives the eyes and what the breasts conceal.",
            "Ghafir 40:19",
            "Ele conhece os olhares furtivos e tudo quanto ocultam os corações.",
            "Ghafir 40:19",
            "يَعْلَمُ خَآئِنَةَ ٱلْأَعْيُنِ وَمَا تُخْفِى ٱلصُّدُورُ",
            "غافر 40:19"
        ),
        BuiltInPassage(
            "Have you seen he who has taken as his god his [own] desire, and Allah has sent him astray due to knowledge and has set a seal upon his hearing and his heart and put over his vision a veil? So who will guide him after Allah? Then will you not be reminded?",
            "Al-Jathiyah 45:23",
            "Não tens reparado, naquele que idolatrou a sua concupiscência! Deus extraviou-o com conhecimento, sigilando os seus ouvidos e o seu coração, e cobriu a sua visão. Quem o iluminará, depois de Deus (tê-lo desencaminhado)? Não meditais, pois?",
            "Al Jássiya 45:23",
            "أَفَرَءَيْتَ مَنِ ٱتَّخَذَ إِلَٰهَهُۥ هَوَىٰهُ وَأَضَلَّهُ ٱللَّهُ عَلَىٰ عِلْمٍۢ وَخَتَمَ عَلَىٰ سَمْعِهِۦ وَقَلْبِهِۦ وَجَعَلَ عَلَىٰ بَصَرِهِۦ غِشَٰوَةًۭ فَمَن يَهْدِيهِ مِنۢ بَعْدِ ٱللَّهِ ۚ أَفَلَا تَذَكَّرُونَ",
            "الجاثية 45:23"
        ),
        BuiltInPassage(
            "But as for he who feared the position of his Lord and prevented the soul from [unlawful] inclination, then indeed, Paradise will be [his] refuge.",
            "An-Nazi'at 79:40-41",
            "Ao contrário, quem tiver temido o comparecimento ante o seu Senhor e se tiver refreado em relação à luxúria, terá o Paraíso por abrigo.",
            "An Názi'at 79:40-41",
            "وَأَمَّا مَنْ خَافَ مَقَامَ رَبِّهِۦ وَنَهَى ٱلنَّفْسَ عَنِ ٱلْهَوَىٰ فَإِنَّ ٱلْجَنَّةَ هِىَ ٱلْمَأْوَىٰ",
            "النازعات 79:40-41"
        ),
        BuiltInPassage(
            "He has certainly succeeded who purifies himself and mentions the name of his Lord and prays.",
            "Al-A'la 87:14-15",
            "Bem-aventurado aquele que se purificar, e mencionar o nome do seu Senhor e orar!",
            "Al A'la 87:14-15",
            "قَدْ أَفْلَحَ مَن تَزَكَّىٰ وَذَكَرَ ٱسْمَ رَبِّهِۦ فَصَلَّىٰ",
            "الأعۡلى 87:14-15"
        ),
        BuiltInPassage(
            "And [by] the soul and He who proportioned it and inspired it [with discernment of] its wickedness and its righteousness, he has succeeded who purifies it, and he has failed who instills it [with corruption].",
            "Ash-Shams 91:7-10",
            "Pela alma e por Quem aperfeiçoou, e lhe imprimiu o discernimento entre o que é certo e o que é errado, que será venturoso quem a purificar (a alma), e desventurado quem a corromper.",
            "Ash Shams 91:7-10",
            "وَنَفْسٍۢ وَمَا سَوَّىٰهَا فَأَلْهَمَهَا فُجُورَهَا وَتَقْوَىٰهَا قَدْ أَفْلَحَ مَن زَكَّىٰهَا وَقَدْ خَابَ مَن دَسَّىٰهَا",
            "الشمۡس 91:7-10"
        )
    )

    private val QUOTES = listOf(
        BuiltInPassage(
            "Most gladly have I escaped the thing of which you speak; I feel as if I had escaped from a mad and furious master.",
            "Sophocles, in Plato — The Republic, Book I",
            "Com toda a alegria escapei daquilo de que falas; sinto-me como quem escapou de um senhor louco e furioso.",
            "Sófocles, em Platão — A República, Livro I"
        ),
        BuiltInPassage(
            "There is a victory and defeat — the first and best of victories, the lowest and worst of defeats — which each man gains or sustains at the hands, not of another, but of himself.",
            "Plato — Laws, Book I",
            "Há uma vitória e uma derrota — a primeira e melhor das vitórias, a mais baixa e pior das derrotas — que cada homem obtém ou sofre não pelas mãos de outro, mas de si mesmo.",
            "Platão — As Leis, Livro I"
        ),
        BuiltInPassage(
            "Does it not come to this, that every honest man is bound to look upon self-restraint as the very corner-stone of virtue, which he should seek to lay down as the basis and foundation of his soul?",
            "Socrates, in Xenophon — Memorabilia, Book I",
            "Não se chega a isto: que todo homem honesto deve ver o autocontrole como a própria pedra angular da virtude, que ele deve assentar como base e fundamento de sua alma?",
            "Sócrates, em Xenofonte — Memoráveis, Livro I"
        ),
        BuiltInPassage(
            "By doing just actions we come to be just; by doing the actions of self-mastery we come to be perfected in self-mastery; and by doing brave actions brave.",
            "Aristotle — Nicomachean Ethics, Book II",
            "Praticando ações justas nos tornamos justos; praticando ações de domínio de si nos aperfeiçoamos no domínio de si; e praticando ações corajosas, corajosos.",
            "Aristóteles — Ética a Nicômaco, Livro II"
        ),
        BuiltInPassage(
            "It is easier to banish dangerous passions than to rule them; it is easier not to admit them than to keep them in order when admitted.",
            "Seneca — On Anger, Book I",
            "É mais fácil banir as paixões perigosas do que governá-las; é mais fácil não admiti-las do que mantê-las em ordem depois de admitidas.",
            "Sêneca — Sobre a Ira, Livro I"
        ),
        BuiltInPassage(
            "To rule oneself is the greatest of all rule.",
            "Seneca — Letters to Lucilius, 113",
            "Governar a si mesmo é o maior de todos os governos.",
            "Sêneca — Cartas a Lucílio, 113"
        ),
        BuiltInPassage(
            "Be not hurried away by the rapidity of the appearance, but say, Appearances, wait for me a little; let me see who you are, and what you are about; let me put you to the test. And then do not allow the appearance to lead you on and draw lively pictures of the things which will follow; for if you do, it will carry you off wherever it pleases.",
            "Epictetus — Discourses, Book II",
            "Não te deixes arrastar pela rapidez da impressão, mas dize: Impressão, espera-me um pouco; deixa-me ver quem és e do que se trata; deixa-me pôr-te à prova. E depois não permitas que ela te conduza, pintando imagens vivas do que virá em seguida; pois, se o fizeres, ela te levará para onde quiser.",
            "Epicteto — Diatribes, Livro II"
        ),
        BuiltInPassage(
            "On the occasion of every accident that befalls you, remember to turn to yourself and inquire what power you have for turning it to use. If you see a fair man or a fair woman, you will find that the power to resist is temperance.",
            "Epictetus — Enchiridion, 10",
            "Em cada acontecimento que te sobrevier, lembra-te de voltar-te para ti mesmo e perguntar que poder tens para fazer bom uso dele. Se vires um homem ou uma mulher formosos, descobrirás que o poder para resistir é a temperança.",
            "Epicteto — Manual, 10"
        ),
        BuiltInPassage(
            "Such as thy thoughts and ordinary cogitations are, such will thy mind be in time. For the soul doth as it were receive its tincture from the fancies, and imaginations.",
            "Marcus Aurelius — Meditations, Book V",
            "Tais como forem os teus pensamentos habituais, tal será com o tempo a tua mente. Pois a alma se tinge, por assim dizer, das fantasias e das imaginações.",
            "Marco Aurélio — Meditações, Livro V"
        ),
        BuiltInPassage(
            "My will the enemy held, and thence had made a chain for me, and bound me. For of a forward will, was a lust made; and a lust served, became custom; and custom not resisted, became necessity.",
            "Augustine — Confessions, Book VIII",
            "O inimigo dominava a minha vontade e dela fizera uma corrente com que me prendia. Pois da vontade perversa nasceu a paixão; a paixão servida tornou-se hábito; e o hábito não combatido tornou-se necessidade.",
            "Agostinho — Confissões, Livro VIII"
        ),
        BuiltInPassage(
            "I had begged chastity of Thee, and said, \"Give me chastity and continency, only not yet.\" For I feared lest Thou shouldest hear me soon, and soon cure me of the disease of concupiscence, which I wished to have satisfied, rather than extinguished.",
            "Augustine — Confessions, Book VIII",
            "Eu havia pedido a Ti a castidade, dizendo: \"Dá-me a castidade e a continência, mas não agora.\" Pois temia que me ouvisses logo e logo me curasses da doença da concupiscência, que eu preferia saciar a extinguir.",
            "Agostinho — Confissões, Livro VIII"
        ),
        BuiltInPassage(
            "It is a hard thing to break through a habit, and a yet harder thing to go contrary to our own will. Yet if thou overcome not slight and easy obstacles, how shalt thou overcome greater ones? Withstand thy will at the beginning, and unlearn an evil habit, lest it lead thee little by little into worse difficulties.",
            "Thomas à Kempis — The Imitation of Christ, Book I",
            "É difícil romper com um hábito, e ainda mais difícil contrariar a própria vontade. Mas, se não vences obstáculos pequenos e fáceis, como vencerás os maiores? Resiste à tua vontade desde o princípio e desaprende o mau hábito, para que ele não te leve pouco a pouco a dificuldades maiores.",
            "Tomás de Kempis — A Imitação de Cristo, Livro I"
        ),
        BuiltInPassage(
            "We must watch, especially in the beginnings of temptation; for then is the foe the more easily mastered, when he is not suffered to enter within the mind, but is met outside the door as soon as he hath knocked.",
            "Thomas à Kempis — The Imitation of Christ, Book I",
            "Devemos vigiar, sobretudo no começo da tentação; pois então o inimigo é vencido com mais facilidade, quando não se lhe permite entrar na mente, mas é enfrentado do lado de fora da porta, assim que bate.",
            "Tomás de Kempis — A Imitação de Cristo, Livro I"
        ),
        BuiltInPassage(
            "I have discovered that all the unhappiness of men arises from one single fact, that they cannot stay quietly in their own chamber.",
            "Blaise Pascal — Pensées, 139",
            "Descobri que toda a infelicidade dos homens provém de uma só coisa: não saberem permanecer em repouso num quarto.",
            "Blaise Pascal — Pensamentos, 139"
        ),
        BuiltInPassage(
            "The expense of spirit in a waste of shame\nIs lust in action: and till action, lust\nIs perjur’d, murderous, bloody, full of blame,\nSavage, extreme, rude, cruel, not to trust;\nEnjoy’d no sooner but despised straight.",
            "William Shakespeare — Sonnet 129",
            "O gasto do espírito num desperdício de vergonha\né a luxúria em ação; e, até agir, a luxúria\né perjura, assassina, sanguinária, culpada,\nselvagem, extrema, rude, cruel, traiçoeira;\nmal é gozada, logo é desprezada.",
            "William Shakespeare — Soneto 129"
        ),
        BuiltInPassage(
            "Above all, don’t lie to yourself. The man who lies to himself and listens to his own lie comes to such a pass that he cannot distinguish the truth within him, or around him, and so loses all respect for himself and for others.",
            "Fyodor Dostoevsky — The Brothers Karamazov",
            "Acima de tudo, não minta para si mesmo. Quem mente para si mesmo e dá ouvidos à própria mentira chega a um ponto em que já não distingue a verdade, nem dentro de si nem ao seu redor, e assim perde todo o respeito por si e pelos outros.",
            "Fiódor Dostoiévski — Os Irmãos Karamázov"
        ),
        BuiltInPassage(
            "If one man conquer in battle a thousand times thousand men, and if another conquer himself, he is the greatest of conquerors.",
            "The Buddha — Dhammapada, 103",
            "Se um homem vencer em batalha mil vezes mil homens, e outro vencer a si mesmo, este é o maior dos vencedores.",
            "Buda — Dhammapada, 103"
        ),
        BuiltInPassage(
            "An ever increasing craving for an ever diminishing pleasure is the formula.",
            "C. S. Lewis — The Screwtape Letters",
            "Um desejo cada vez maior por um prazer cada vez menor: essa é a fórmula.",
            "C. S. Lewis — Cartas de um Diabo a Seu Aprendiz"
        ),
        BuiltInPassage(
            "A silly idea is current that good people do not know what temptation means. This is an obvious lie. Only those who try to resist temptation know how strong it is.",
            "C. S. Lewis — Mere Christianity",
            "Circula a ideia tola de que as pessoas boas não sabem o que é tentação. Isso é uma mentira evidente. Só quem tenta resistir à tentação sabe o quanto ela é forte.",
            "C. S. Lewis — Cristianismo Puro e Simples"
        ),
        BuiltInPassage(
            "It was the peculiar artifice of Habit not to suffer her power to be felt at first. […] Each link grew tighter as it had been longer worn, and when, by continual additions, they became so heavy as to be felt, they were very frequently too strong to be broken.",
            "Samuel Johnson — The Vision of Theodore",
            "Era o artifício peculiar do Hábito não deixar que seu poder fosse sentido no início. […] Cada elo apertava mais quanto mais tempo era usado e, quando, por acréscimos contínuos, as correntes ficavam pesadas o bastante para serem sentidas, muitas vezes já eram fortes demais para serem quebradas.",
            "Samuel Johnson — A Visão de Teodoro"
        )
    )
}

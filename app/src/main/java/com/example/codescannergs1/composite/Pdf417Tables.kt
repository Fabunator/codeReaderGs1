package com.example.codescannergs1.composite

/**
 * Tabellen aus ISO/IEC 15438 Annex A (PDF417-Symbolzeichen) und ISO/IEC 15438 Tabelle 2
 * (Row Address Patterns), wie sie auch fuer MicroPDF417 und den CC-A-Anteil eines
 * GS1-Composite-Symbols gelten.
 *
 * Erzeugt aus den Referenztabellen von zint (BSD-3-Clause, Robin Stuart).
 * Als Hex-Strings abgelegt, damit keine 2787 Array-Literale im Bytecode landen.
 */
internal object Pdf417Tables {
    private const val BITPATTERN_HEX: String =
            "eae0f578fabeea70f53cfa9fd460ea38d430a820d418a810d6e0eb78f5bed670eb3cf59fac60d638ac30aee0d778ebbeae70d73ceb9fae38d71eaf78"+
            "d7beaf3cd79fafbefafde970f4bcfa5fd260e938f49ed230e91ca420d218e90ea410d20ca408d370e9bcf4dfa660d338e99ea630d31ce98fa618d30e"+
            "a770d3bce9dfa738d39ea71cd38fa7bcd3dfa79ea78fd160e8b8f45ed130e89cf44fa220d118e88ea210d10ca208a204a360d1b8e8dea330d19ce8cf"+
            "a318d18ea30ca306a3b8d1dea39cd1cfa38ea3ded0b0e85cf42fa120d098e84ea110d08ce847a108d086a104d083a1b0d0dce86fa198d0cea18cd0c7"+
            "a186a183d0efa1c7a0a0d058e82ea090d04ce827a088d046a084d043a082a0d8a0cca0c6a050e817d026d023a041e570f2bcf95fca60e538f29eca30"+
            "e51cf28f9420ca189410cb70e5bcf2df9660cb38e59e9630cb1c9618960c9770cbbce5df9738cb9e971c970e97bccbdf979e97dfed60f6b8fb5eed30"+
            "f69cfb4fda20ed18f68eda10ed0cf687da08ed06c960e4b8f25edb60c930e49cf24fdb30ed9cf6cfb6209210c90ce487b610db0cb6089360c9b8e4de"+
            "b7609330c99ce4cfb730db9cedcfb718930cb70c93b8c9deb7b8939cc9cfb79cdbcfb78e93deb7de93cfb7cfecb0f65cfb2fd920ec98f64ed910ec8c"+
            "f647d908ec86d904d902c8b0e45cf22fd9b0c898e44eb3209110eccee447b3109108c886b308d986c883910291b0c8dce46fb3b09198c8ceb398d9ce"+
            "c8c7b38c9186918391dcc8efb3dc91ceb3ce91c7b3c7b3efd8a0ec58f62ed890ec4cf627d888ec46d884ec43d882d88190a0c858e42eb1a09090c84c"+
            "e427b190d8ccec67b1889084c843b184d8c3b18290d8c86eb1d890ccc867b1ccd8e7b1c690c3b1c3b1eeb1e7d850ec2cf617d848ec26d844ec23d842"+
            "d8419050c82ce417b0d09048c826b0c8d866c823b0c49042b0c29041906cb0ecb0e6b0e3ec16ec13d821c8169024b064b062b061c560e2b8f15ec530"+
            "e29c8a20c518e28e8a10c50c8a088a048b60c5b8e2de8b30c59ce2cf8b18c58e8b0c8b068bb8c5de8b9cc5cf8b8e8bde8bcfe6b0f35cf9afcd20e698"+
            "f34ecd10e68cf347cd08e686cd04e683c4b0e25cf12fcdb0c498e24e9b208910e6cee2479b10cd8cc4869b0889049b0489b0c4dce26f9bb08998e6ef"+
            "9b98cdcec4c79b8c89869b8689dcc4ef9bdc89ce9bce89c789ef9befeea0f758fbaeee90f74cfba7ee88f746ee84f743ee82cca0e658f32edda0cc90"+
            "f76ef327dd90eeccf767dd88cc84e643dd84eec3cc8188a0c458e22e99a08890c44ce227bba09990cccce667bb90ddcceee7c443bb889984ccc3bb84"+
            "888188d8c46e99d888ccc467bbd899cccce7bbccdde788c399c388ee99ee88e7bbee99e7ee50f72cfb97ee48f726ee44f723ee42ee41cc50e62cf317"+
            "dcd0cc48f737dcc8ee66e623dcc4cc42dcc2cc41dcc18850c42ce21798d08848c426b9d098c8cc66c423b9c8dce68842b9c498c2884198c1886cc437"+
            "98ec8866b9ec98e68863b9e698e38877b9f7ee28f716ee24f713ee22ee21cc28e616dc68cc24e613dc64ee33dc62cc21dc618828c41698688824c413"+
            "b8e89864cc33b8e4dc738821b8e29861b8e19876b8f6b8f3f70bee11e60bcc12cc1188149834b87488119831c2b08520c2988510c28ce1478508c286"+
            "8504c28385b0c2dce16f8598c2ce858cc2c78586858385dcc2ef85ce85c785efc6a0e358f1aec690e34cc688e346c684e343c68284a0c258e12e8da0"+
            "8490e36ee1278d90c6cce3678d888484c2438d84c6c3848184d8c26e8dd884ccc2678dccc6e78dc684c384ee8dee84e78de7e750f3acf9d7e748f3a6"+
            "e744f3a3e742e741c650e32cced0c648e326cec8e766e323cec4c642cec2c641cec18450c22c8cd08448e3379dd08cc8c666c2239dc8cee684429dc4"+
            "8cc284418cc1846cc2378cec84669dec8ce684639de68ce384778cf79df7f7a8fbd6f7a4fbd3f7a2f7a1e728f396ef68f7b6f393ef64f7b3ef62e721"+
            "ef61c628e316ce68c624e313dee8ce64e733dee4ef73c621dee2ce61dee18428c2168c688424c2139ce88c64c633bde89ce4ce738421bde4def38c61"+
            "bde284368c7684339cf68c73bdf69cf3bdf3f794fbcbf792f791e714f38bef34f79bef32e711ef31c614e30bce34c612de74ce32c611de72ce31de71"+
            "8414c20b8c34c61b9c748c328411bcf49c728c31bcf29c71bcf18c3bbcfbf789ef1aef19ce1ade3ade398c1a9c3abc7abc7982a08290c14c82888284"+
            "828282d882cc82c682c382ee82e7c350c348e1a6c344e1a3c342c3418250c12c86d0c36cc12686c8c36686c4c36386c2824186c1826cc13786ecc377"+
            "86e6826386e3827786f7e3a8e3a4e3a2e3a1c328c768e3b6e193c764e3b3c762c321c761822886688224c1138ee8866482228ee4866282218ee28661"+
            "8236867682338ef686738ef3f3d4f3d2f3d1e394e7b4f3dbe7b2e391e7b1c314e18bc734e39bcf74c732c311cf72c731cf718214c10b8634c31b8e74"+
            "863282119ef48e7286319ef28e71821b863b8e7b9efbfbeafbe9f3caf7daf3c9f7d9e38ae79ae389efbae799efb9c30ac71ac309cf3ac719df7afab0"+
            "fd5cf520fa98fd4ef510fa8cfd47f508fa86f504fa83f502f5b0fadcfd6feb20f598faceeb10f58cfac7eb08f586eb04f583eb02ebb0f5dcfaefd720"+
            "eb98f5ced710eb8cf5c7d708eb86d704eb83d702d7b0ebdcf5efaf20d798ebceaf10d78cebc7af08d786af04d783afb0d7dcebefaf98d7ceaf8cd7c7"+
            "af86afdcd7efafceafc7f4a0fa58fd2ef490fa4cfd27f488fa46f484fa43f482f481e9a0f4d8fa6ee990f4ccfa67e988f4c6e984f4c3e982e981d3a0"+
            "e9d8f4eed390e9ccf4e7d388e9c6d384e9c3d382d381a7a0d3d8e9eea790d3cce9e7a788d3c6a784d3c3a782a7d8d3eea7ccd3e7a7c6a7c3a7eea7e7"+
            "f450fa2cfd17f448fa26f444fa23f442f441e8d0f46cfa37e8c8f466e8c4f463e8c2e8c1d1d0e8ecf477d1c8e8e6d1c4e8e3d1c2d1c1a3d0d1ece8f7"+
            "a3c8d1e6a3c4d1e3a3c2a3c1a3ecd1f7a3e6a3e3a3f7f428fa16f424fa13f422f421e868f436e864f433e862e861d0e8e876d0e4e873d0e2d0e1a1e8"+
            "d0f6a1e4d0f3a1e2a1e1a1f6a1f3f414fa0bf412f411e834f41be832e831d074e83bd072d071a0f4d07ba0f2a0f1f40af409e81ae819d03ad039f2a0"+
            "f958fcaef290f94cfca7f288f946f284f943f282f281e5a0f2d8f96ee590f2ccf967e588f2c6e584f2c3e582e581cba0e5d8f2eecb90e5ccf2e7cb88"+
            "e5c6cb84e5c3cb82cb8197a0cbd8e5ee9790cbcce5e79788cbc69784cbc3978297d8cbee97cccbe797c697c397ee97e7fb50fdacb5f8fb48fda6b4fc"+
            "fb44fda3b47efb42fb41f250f92cfc97f6d0f248fdb7f6c8fb66f923f6c4f242f6c2f241f6c1e4d0f26cf937edd0e4c8f266edc8f6e6f263edc4e4c2"+
            "edc2e4c1edc1c9d0e4ecf277dbd0c9c8e4e6dbc8ede6e4e3dbc4c9c2dbc2c9c1dbc193d0c9ece4f7b7d093c8c9e6b7c8dbe6c9e3b7c493c2b7c293c1"+
            "93ecc9f7b7ec93e6b7e693e3b7e393f7fb28fd96b2fcfb24fd93b27efb22b23ffb21f228f916f668f224f913f664fb33f662f221f661e468f236ece8"+
            "e464f233ece4f673ece2e461ece1c8e8e476d9e8c8e4e473d9e4ecf3d9e2c8e1d9e191e8c8f6b3e891e4c8f3b3e4d9f3b3e291e1b3e191f6b3f691f3"+
            "b3f3fb14fd8bb17efb12b13ffb11f214f90bf634fb1bf632f211f631e434f21bec74e432ec72e431ec71c874e43bd8f4ec7bd8f2c871d8f190f4c87b"+
            "b1f490f2b1f290f1b1f190fbb1fbfb0ab0bffb09f20af61af209f619e41aec3ae419ec39c83ad87ac839d879907ab0fa9079b0f9fb05f205f60de40d"+
            "ec1dc81dd83df150f8acfc57f148f8a6f144f8a3f142f141e2d0f16cf8b7e2c8f166e2c4f163e2c2e2c1c5d0e2ecf177c5c8e2e6c5c4e2e3c5c2c5c1"+
            "8bd0c5ece2f78bc8c5e68bc4c5e38bc28bc18becc5f78be68be38bf7f9a8fcd69afcf9a4fcd39a7ef9a29a3ff9a1f128f896f368f124f893f364f9b3"+
            "f362f121f361e268f136e6e8e264f133e6e4f373e6e2e261e6e1c4e8e276cde8c4e4e273cde4e6f3cde2c4e1cde189e8c4f69be889e4c4f39be4cdf3"+
            "9be289e19be189f69bf689f39bf3fdd4baf8dd7efdd2ba7cdd3ffdd1ba3eba1ff994fccb997efbb4fddbbb7e993ffbb2f991bb3ffbb1f114f88bf334"+
            "f112f774fbbbf111f772f331f771e234f11be674e232eef4e672e231eef2e671eef1c474e23bccf4c472ddf4ccf2c471ddf2ccf1ddf188f4c47b99f4"+
            "88f2bbf499f288f1bbf299f1bbf188fb99fbfdcab97cdcbffdc9b93eb91ff98a98bffb9af989b9bffb99f10af31af109f73af319f739e21ae63ae219"+
            "ee7ae639ee79c43acc7ac439dcfacc79dcf9887a98fa8879b9fa98f9b9f9fdc5b8beb89ff985fb8df105f30df71de20de61dee3dc41dcc3ddc7d883d"+
            "987db8fdb85ff0a8f856f0a4f853f0a2f0a1e168f0b6e164f0b3e162e161c2e8e176c2e4e173c2e2c2e185e8c2f685e4c2f385e285e185f685f3f8d4"+
            "fc6b8d7ef8d28d3ff8d1f094f84bf1b4f092f1b2f091f1b1e134f09be374e132e372e131e371c274e13bc6f4c272c6f2c271c6f184f4c27b8df484f2"+
            "8df284f18df184fb8dfbfcea9d7ccebffce99d3e9d1ff8ca8cbff9daf8c99dbff9d9f08af19af089f3baf199f3b9e11ae33ae119e77ae339e779c23a"+
            "c67ac239cefac679cef9847a8cfa84799dfa8cf99df9bd78debebd3cde9fbd1ebd0ffce59cbefdedbdbe9c9fbd9ff8c5f9cdfbddf085f18df39df7bd"+
            "e10de31de73def7dc21dc63dce7ddefd843d8c7d9cfdbcbcde5fbc9ebc8f9c5fbcdfbc5ebc4fbc2ff054f052f051e0b4f05be0b2e0b1c174e0bbc172"+
            "c17182f4c17b82f282f182fbf86a86bff869f04af0daf049f0d9e09ae1bae099e1b9c13ac37ac139c379827a86fa827986f9fc758ebe8e9ff865f8ed"+
            "f045f0cdf1dde08de19de3bdc11dc33dc77d823d867d8efd9ebccf5f9e9e9e8f8e5f9edfbeb8df5ebe9cdf4fbe8ebe879e5ebede9e4fbecfbe5cdf2f"+
            "be4ebe479e2fbe6fbe2ebe27be17e05ae059c0bac0b9817a8179f06de04de0ddc09dc1bd813d837d875f8f5e8f4f9f5ccfaf9f4e9f478f2f9f6fbf58"+
            "dfaebf4cdfa7bf46bf439f2ebf6e9f27bf67bf2cdf97bf26bf239f17bf37bf16bf1387af8fae8fa79faccfd79fa69fa38f979fb79f969f93d5f0eafc"+
            "a9e0d4f8ea7ea8f0d47cea3fa878d43ea83cfd68adf0d6fcfd64acf8d67efd62ac7cd63ffd61ac3efae8fd76aefcfae4fd73ae7efae2ae3ffae1f5e8"+
            "faf6f5e4faf3f5e2f5e1ebe8f5f6ebe4f5f3ebe2ebe1d7e8ebf6d7e4ebf3d7e2a5e0d2f8e97ea4f0d27ce93fa478d23ea43cd21fa41efd34a6f8d37e"+
            "fd32a67cd33ffd31a63ea61ffa74fd3ba77efa72a73ffa71f4f4fa7bf4f2f4f1e9f4f4fbe9f2e9f1d3f4e9fbd3f2d3f1a2f0d17ce8bfa278d13ea23c"+
            "d11fa21ea20ffd1aa37cd1bffd19a33ea31ffa3aa3bffa39f47af479e8fae8f9d1fad1f9a178d0bea13cd09fa11ea10ffd0da1bea19ffa1df43de87d"+
            "a0bcd05fa09ea08fa0dfa05ea04f95e0caf8e57e94f0ca7ce53f9478ca3e943cca1f941efcb496f8cb7efcb2967ccb3ffcb1963e961ff974fcbb977e"+
            "f972973ff971f2f4f97bf2f2f2f1e5f4f2fbe5f2e5f1cbf4e5fbcbf2cbf1daf0ed7cf6bfb4e0da78ed3eb470da3ced1fb438da1eb41cda0fb40e92f0"+
            "c97ce4bfb6f09278c93eb678db3ec91fb63c921eb61e920fb60ffc9a937cc9bffdbafc99b77c933efdb9b73e931fb71ff93a93bffb7af939b7bffb79"+
            "f27af6faf279f6f9e4faedfae4f9edf9c9fac9f9b2e0d978ecbeb270d93cec9fb238d91eb21cd90fb20eb2079178c8beb378913cc89fb33cd99fb31e"+
            "910fb30ffc8d91befd9db3be919fb39ff91dfb3df23df67de47decfdc8fdb170d8bcec5fb138d89eb11cd88fb10eb10790bcc85fb1bc909eb19e908f"+
            "b18f90dfb1dfb0b8d85eb09cd84fb08eb087905eb0de904fb0cfb05cd82fb04eb047902fb06fb02eb0278af0c57ce2bf8a78c53e8a3cc51f8a1e8a0f"+
            "fc5a8b7cc5bffc598b3e8b1ff8ba8bbff8b9f17af179e2fae2f9c5fac5f99ae0cd78e6be9a70cd3ce69f9a38cd1e9a1ccd0f9a0e9a078978c4be9b78"+
            "893cc49f9b3ccd9f9b1e890f9b0ffc4d89befcdd9bbe899f9b9ff89df9bdf13df37de27de6fdc4fddd70eebcf75fba60dd38ee9eba30dd1cee8fba18"+
            "dd0eba0cdd07ba069970ccbce65fbb709938cc9ebb38dd9ecc8fbb1c990ebb0e9907bb0788bcc45f99bc889ebbbc999e888fbb9e998fbb8f88df99df"+
            "bbdfb960dcb8ee5eb930dc9cee4fb918dc8eb90cdc87b906b90398b8cc5eb9b8989ccc4fb99cdccfb98e9887b987885e98de884fb9de98cfb9cfb8b0"+
            "dc5cee2fb898dc4eb88cdc47b886b883985ccc2fb8dc984eb8ce9847b8c7882f986fb8efb858dc2eb84cdc27b846b843982eb86e9827b867b82cdc17"+
            "b826b8239817b837b816b8138578c2be853cc29f851e850f85be859ff85df0bde17dc2fd8d70c6bce35f8d38c69e8d1cc68f8d0e8d0784bcc25f8dbc"+
            "849e8d9e848f8d8f84df8ddf9d60ceb8e75e9d30ce9ce74f9d18ce8e9d0cce879d069d038cb8c65e9db88c9cc64f9d9c8c8e9d8e8c879d87845e8cde"+
            "844f9dde8ccf9dcfdeb0ef5cf7afbd20de98ef4ebd10de8cef47bd08de86bd04de83bd029cb0ce5ce72fbdb09c98ce4ebd98decece47bd8c9c86bd86"+
            "9c83bd838c5cc62f9cdc8c4ebddc9cce8c47bdce9cc7bdc7842f8c6f9cefbdefbca0de58ef2ebc90de4cef27bc88de46bc84de43bc82bc819c58ce2e"+
            "bcd89c4cce27bcccde67bcc69c43bcc38c2e9c6e8c27bcee9c67bce7bc50de2cef17bc48de26bc44de23bc42bc419c2cce17bc6c9c26bc669c23bc63"+
            "8c179c37bc77bc28de16bc24de13bc22bc219c16bc369c13bc33bc14de0bbc12bc119c0bbc1b82bcc15f829e828f82df86b8c35e869cc34f868e8687"+
            "825e86de824f86cf8eb0c75ce3af8e98c74e8e8cc7478e868e83865cc32f8edc864e8ece86478ec7822f866f8eef9ea0cf58e7ae9e90cf4ce7a79e88"+
            "cf469e84cf439e829e818e58c72e9ed88e4cc7279ecccf679ec68e439ec3862e8e6e86279eee8e679ee7df50efacf7d7df48efa6df44efa3df42df41"+
            "9e50cf2ce797bed09e48cf26bec8df66cf23bec49e42bec29e41bec18e2cc7179e6c8e26beec9e668e23bee69e63bee386178e379e77bef7df28ef96"+
            "df24ef93df22df219e28cf16be689e24cf13be64df33be629e21be618e169e368e13be769e33be73df14ef8bdf12df119e14cf0bbe349e12be329e11"+
            "be318e0b9e1bbe3bdf0adf099e0abe1a9e09be19815e814f835cc1af834e8347812f836f8758c3ae874cc3a787468743832e876e832787678f50c7ac"+
            "e3d78f48c7a68f44c7a38f428f41872cc3978f6cc7b78f6687238f63831787378f77cfa8e7d6cfa4e7d3cfa2cfa18f28c7969f68cfb6c7939f648f22"+
            "9f628f219f6187168f3687139f768f339f73efd4f7ebefd2efd1cf94e7cbdfb4cf92dfb2cf91dfb18f14c78b9f348f12bf749f328f11bf729f31bf71"+
            "870b8f1b9f3bbf7befcaefc9cf8adf9acf89df998f0a9f1a8f09bf3a9f19bf39efc5cf85df8d8f059f0dbf1d81ae81a783acc1d783a683a3819783b7"+
            "87a8c3d687a4c3d387a287a1839687b6839387b3c7d4e3ebc7d2c7d18794c3cb8fb4c7db8fb287918fb1838b879b8fbbe7eae7e9c7cacfdac7c9cfd9"+
            "878a8f9a87899fba8f999fb9e7e5c7c5cfcd87858f8d9f9d81d681d383d4c1eb83d283d181cb83dbc3eac3e983ca87da83c987d9e3f5"

    private const val RAP_SIDE_HEX: String =
            "3223a23b233237237a33a3ba39a3da3ca38a30a31a3123923d23d63d43943b43a43a63ae3ac3a832832c32e3263363b639631631433437436436636e"+
            "36c36834835835c35e34e34c344346342362"

    private const val RAP_CENTRE_HEX: String =
            "2ce24e26e22e22623621621221a23a23222226227227a2fa2f22f62762742642662462422c22e22e62e42ec26c22c2282682e82c82cc2c42c628628e"+
            "28c29c2982b82b02902d025025825c2dc2de"

    /** 929 Symbolzeichen je Cluster (0, 3, 6) – 16-Bit-Muster, links ausgerichtet. */
    val bitPattern: IntArray = parse(BITPATTERN_HEX, 4)

    /** Left/Right Row Address Pattern, 10 Module. */
    val rapSide: IntArray = parse(RAP_SIDE_HEX, 3)

    /** Centre Row Address Pattern, 10 Module. */
    val rapCentre: IntArray = parse(RAP_CENTRE_HEX, 3)

    /**
     * 17-Bit-Modulmuster -> (Cluster shl 16) or Codewortwert.
     * Die drei Cluster sind disjunkt, ein Muster bestimmt also Cluster und Wert eindeutig.
     */
    val patternToCodeword: HashMap<Int, Int> = HashMap<Int, Int>(4096).also { map ->
        for (cluster in 0 until 3) {
            for (value in 0 until 929) {
                val pattern = (bitPattern[929 * cluster + value] shl 1) and 0x1FFFF
                map[pattern] = (cluster shl 16) or value
            }
        }
    }

    private fun parse(hex: String, width: Int): IntArray {
        val n = hex.length / width
        val out = IntArray(n)
        for (i in 0 until n) {
            out[i] = hex.substring(i * width, (i + 1) * width).toInt(16)
        }
        return out
    }
}

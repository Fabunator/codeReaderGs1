package com.example.codescannergs1

// ---------------------------------------------------------------------------
// GENERIERT von der Gradle-Task generateGs1AiDictionary (app/gs1-ai.gradle.kts)
// – nicht von Hand bearbeiten. Quellen aktualisieren: gradlew updateGs1Ai
//
// Quelle: GS1 Barcode Syntax Dictionary (Release: UNSET)
//         https://github.com/gs1/gs1-syntax-dictionary – Datensatz hinter https://ref.gs1.org/ai/
// Copyright (c) 2020-2021 BWIPP project, (c) 2020-2021 Zint Project, (c) 2021-2025 GS1 AISBL
// Licensed under the Apache License, Version 2.0
//
// Zeilenformat:  AIs  [Flags]  Spezifikation  [Attribute...]  [# Titel]
//   AIs     einzelne AI oder Bereich, z. B. "3100-3105"
//   Flags   "*" = vordefinierte Laenge, kein FNC1 danach; "?" = Digital-Link-Attribut
//   Spez.   Komponenten Typ[,Linter...]: N = numerisch, X = CSET 82, Y = CSET 39,
//           Z = base64url; "N6" feste, "X..20" variable Laenge, "[...]" optional
// Ausgewertet in GS1Parser.
// ---------------------------------------------------------------------------

internal const val GS1_SYNTAX_DICTIONARY = """
00         *?  N18,csum,gcppos2                  dlpkey                                             # SSCC
01         *?  N14,csum,gcppos2                  ex=255,37 dlpkey=22,10,21|235                      # GTIN
02         *?  N14,csum,gcppos2                  ex=01,03 req=37                                    # CONTENT
03         *   N14,csum,gcppos2                  ex=01,02,37,235                                    # MTO GTIN
10          ?  X..20                             req=01,02,03,8006,8026                             # BATCH/LOT
11         *?  N6,yymmd0                         req=01,02,03,8006,8026                             # PROD DATE
12         *?  N6,yymmd0                         req=8020                                           # DUE DATE
13         *?  N6,yymmd0                         req=01,02,03,8006,8026                             # PACK DATE
15         *?  N6,yymmd0                         req=01,02,03,8006,8026                             # BEST BEFORE or BEST BY
16         *?  N6,yymmd0                         req=01,02,03,8006,8026                             # SELL BY
17         *?  N6,yymmd0                         req=01,02,03,255,8006,8026                         # USE BY or EXPIRY
20         *?  N2                                req=01,02,03,8006,8026                             # VARIANT
21             X..20                             req=01,03,8006 ex=235                              # SERIAL
22             X..20                             req=01                                             # CPV
235            X..28                             req=01                                             # TPX
240         ?  X..30                             req=01,02,03,8006,8026                             # ADDITIONAL ID
241         ?  X..30                             req=01,02,03,8006,8026                             # CUST. PART No.
242         ?  N..6                              req=01,02,8006,8026                                # MTO VARIANT
243         ?  X..20                             req=01,03                                          # PCN
250         ?  X..30                             req=01+21,03+21,8006+21                            # SECONDARY SERIAL
251         ?  X..30                             req=01,03,8006                                     # REF. TO SOURCE
253         ?  N13,csum,gcppos1 [X..17]          dlpkey                                             # GDTI
254            X..20                             req=414                                            # GLN EXTENSION COMPONENT
255         ?  N13,csum,gcppos1 [N..12]          dlpkey ex=01,02,415,8006,8020,8026                 # GCN
30          ?  N..8                              req=01,02                                          # VAR. COUNT
3100-3105  *?  N6                                req=01,02 ex=310n                                  # NET WEIGHT (kg)
3110-3115  *?  N6                                req=01,02 ex=311n                                  # LENGTH (m)
3120-3125  *?  N6                                req=01,02 ex=312n                                  # WIDTH (m)
3130-3135  *?  N6                                req=01,02 ex=313n                                  # HEIGHT (m)
3140-3145  *?  N6                                req=01,02 ex=314n                                  # AREA (m²)
3150-3155  *?  N6                                req=01,02 ex=315n                                  # NET VOLUME (l)
3160-3165  *?  N6                                req=01,02 ex=316n                                  # NET VOLUME (m³)
3200-3205  *?  N6                                req=01,02 ex=320n                                  # NET WEIGHT (lb)
3210-3215  *?  N6                                req=01,02 ex=321n                                  # LENGTH (in)
3220-3225  *?  N6                                req=01,02 ex=322n                                  # LENGTH (ft)
3230-3235  *?  N6                                req=01,02 ex=323n                                  # LENGTH (yd)
3240-3245  *?  N6                                req=01,02 ex=324n                                  # WIDTH (in)
3250-3255  *?  N6                                req=01,02 ex=325n                                  # WIDTH (ft)
3260-3265  *?  N6                                req=01,02 ex=326n                                  # WIDTH (yd)
3270-3275  *?  N6                                req=01,02 ex=327n                                  # HEIGHT (in)
3280-3285  *?  N6                                req=01,02 ex=328n                                  # HEIGHT (ft)
3290-3295  *?  N6                                req=01,02 ex=329n                                  # HEIGHT (yd)
3300-3305  *?  N6                                req=00,01 ex=330n                                  # GROSS WEIGHT (kg)
3310-3315  *?  N6                                req=00,01 ex=331n                                  # LENGTH (m), log
3320-3325  *?  N6                                req=00,01 ex=332n                                  # WIDTH (m), log
3330-3335  *?  N6                                req=00,01 ex=333n                                  # HEIGHT (m), log
3340-3345  *?  N6                                req=00,01 ex=334n                                  # AREA (m²), log
3350-3355  *?  N6                                req=00,01 ex=335n                                  # VOLUME (l), log
3360-3365  *?  N6                                req=00,01 ex=336n                                  # VOLUME (m³), log
3370-3375  *?  N6                                req=01    ex=337n                                  # KG PER m²
3400-3405  *?  N6                                req=00,01 ex=340n                                  # GROSS WEIGHT (lb)
3410-3415  *?  N6                                req=00,01 ex=341n                                  # LENGTH (in), log
3420-3425  *?  N6                                req=00,01 ex=342n                                  # LENGTH (ft), log
3430-3435  *?  N6                                req=00,01 ex=343n                                  # LENGTH (yd), log
3440-3445  *?  N6                                req=00,01 ex=344n                                  # WIDTH (in), log
3450-3455  *?  N6                                req=00,01 ex=345n                                  # WIDTH (ft), log
3460-3465  *?  N6                                req=00,01 ex=346n                                  # WIDTH (yd), log
3470-3475  *?  N6                                req=00,01 ex=347n                                  # HEIGHT (in), log
3480-3485  *?  N6                                req=00,01 ex=348n                                  # HEIGHT (ft), log
3490-3495  *?  N6                                req=00,01 ex=349n                                  # HEIGHT (yd), log
3500-3505  *?  N6                                req=01,02 ex=350n                                  # AREA (in²)
3510-3515  *?  N6                                req=01,02 ex=351n                                  # AREA (ft²)
3520-3525  *?  N6                                req=01,02 ex=352n                                  # AREA (yd²)
3530-3535  *?  N6                                req=00,01 ex=353n                                  # AREA (in²), log
3540-3545  *?  N6                                req=00,01 ex=354n                                  # AREA (ft²), log
3550-3555  *?  N6                                req=00,01 ex=355n                                  # AREA (yd²), log
3560-3565  *?  N6                                req=01,02 ex=356n                                  # NET WEIGHT (tr oz)
3570-3575  *?  N6                                req=01,02 ex=357n                                  # NET VOLUME (oz)
3600-3605  *?  N6                                req=01,02 ex=360n                                  # NET VOLUME (qt (US))
3610-3615  *?  N6                                req=01,02 ex=361n                                  # NET VOLUME (gal.)
3620-3625  *?  N6                                req=00,01 ex=362n                                  # VOLUME (qt (US)), log
3630-3635  *?  N6                                req=00,01 ex=363n                                  # VOLUME (gal (US)), log
3640-3645  *?  N6                                req=01,02 ex=364n                                  # NET VOLUME (in³)
3650-3655  *?  N6                                req=01,02 ex=365n                                  # NET VOLUME (ft³)
3660-3665  *?  N6                                req=01,02 ex=366n                                  # NET VOLUME (yd³)
3670-3675  *?  N6                                req=00,01 ex=367n                                  # VOLUME (in³), log
3680-3685  *?  N6                                req=00,01 ex=368n                                  # VOLUME (ft³), log
3690-3695  *?  N6                                req=00,01 ex=369n                                  # VOLUME (yd³), log
37          ?  N..8                              req=00+02,00+8026                                  # COUNT
3900-3909   ?  N..15                             req=255,8020 ex=390n,391n,394n,8111                # AMOUNT
3910-3919   ?  N3,iso4217 N..15                  req=8020 ex=391n                                   # AMOUNT
3920-3929   ?  N..15  req=01+30,01+31nn,01+32nn,01+35nn,01+36nn ex=392n,393n                        # PRICE
3930-3939   ?  N3,iso4217 N..15                  req=30,31nn,32nn,35nn,36nn ex=393n                 # PRICE
3940-3943   ?  N4                                req=255 ex=394n,8111                               # PRCNT OFF
3950-3955   ?  N6  req=30,31nn,32nn,35nn,36nn ex=392n,393n,395n,8005                                # PRICE/UoM
400         ?  X..30                                                                                # ORDER NUMBER
401         ?  X..30,gcppos1                     dlpkey                                             # GINC
402         ?  N17,csum,gcppos1                  dlpkey                                             # GSIN
403         ?  X..30                             req=00                                             # ROUTE
410        *?  N13,csum,gcppos1                                                                     # SHIP TO LOC
411        *?  N13,csum,gcppos1                                                                     # BILL TO
412        *?  N13,csum,gcppos1                                                                     # PURCHASE FROM
413        *?  N13,csum,gcppos1                                                                     # SHIP FOR LOC
414        *?  N13,csum,gcppos1                  dlpkey=254|7040                                    # LOC No.
415        *?  N13,csum,gcppos1                  req=8020 dlpkey=8020                               # PAY TO
416        *?  N13,csum,gcppos1                                                                     # PROD/SERV LOC
417        *?  N13,csum,gcppos1                  dlpkey=7040                                        # PARTY
420         ?  X..20                             ex=421                                             # SHIP TO POST
421         ?  N3,iso3166 X..9                   ex=4307                                            # SHIP TO POST
422         ?  N3,iso3166                        req=01,02,03,8006,8026 ex=426                      # ORIGIN
423         ?  N3,iso3166 [N3],iso3166 [N3],iso3166 [N3],iso3166 [N3],iso3166  req=01,02,03 ex=426  # COUNTRY - INITIAL PROCESS
424         ?  N3,iso3166                        req=01,02,03 ex=426                                # COUNTRY - PROCESS
425         ?  N3,iso3166 [N3],iso3166 [N3],iso3166 [N3],iso3166 [N3],iso3166  req=01,02,03 ex=426  # COUNTRY - DISASSEMBLY
426         ?  N3,iso3166                        req=01,02,03                                       # COUNTRY - FULL PROCESS
427         ?  X..3                              req=01+422,02+422,03+422                           # ORIGIN SUBDIVISION
4300        ?  X..35,pcenc                       req=00                                             # SHIP TO COMP
4301        ?  X..35,pcenc                       req=00                                             # SHIP TO NAME
4302        ?  X..70,pcenc                       req=00                                             # SHIP TO ADD1
4303        ?  X..70,pcenc                       req=4302                                           # SHIP TO ADD2
4304        ?  X..70,pcenc                       req=00                                             # SHIP TO SUB
4305        ?  X..70,pcenc                       req=00                                             # SHIP TO LOC
4306        ?  X..70,pcenc                       req=00                                             # SHIP TO REG
4307        ?  X2,iso3166alpha2                  req=00                                             # SHIP TO COUNTRY
4308        ?  X..30                             req=00                                             # SHIP TO PHONE
4309        ?  N10,latitude N10,longitude        req=00                                             # SHIP TO GEO
4310        ?  X..35,pcenc                       req=00                                             # RTN TO COMP
4311        ?  X..35,pcenc                       req=00                                             # RTN TO NAME
4312        ?  X..70,pcenc                       req=00                                             # RTN TO ADD1
4313        ?  X..70,pcenc                       req=4312                                           # RTN TO ADD2
4314        ?  X..70,pcenc                       req=00                                             # RTN TO SUB
4315        ?  X..70,pcenc                       req=00                                             # RTN TO LOC
4316        ?  X..70,pcenc                       req=00                                             # RTN TO REG
4317        ?  X2,iso3166alpha2                  req=00                                             # RTN TO COUNTRY
4318        ?  X..20                             req=00                                             # RTN TO POST
4319        ?  X..30                             req=00                                             # RTN TO PHONE
4320        ?  X..35,pcenc                       req=00                                             # SRV DESCRIPTION
4321        ?  N1,yesno                          req=00                                             # DANGEROUS GOODS
4322        ?  N1,yesno                          req=00                                             # AUTH TO LEAVE
4323        ?  N1,yesno                          req=00                                             # SIG REQUIRED
4324        ?  N6,yymmd0 N4,hhmi                 req=00                                             # NOT BEF DEL DT
4325        ?  N6,yymmd0 N4,hhmi                 req=00                                             # NOT AFT DEL DT
4326        ?  N6,yymmdd                         req=00                                             # REL DATE
4330        ?  N6 [X1],hyphen                    req=00 ex=4331                                     # MAX TEMP F.
4331        ?  N6 [X1],hyphen                    req=00 ex=4330                                     # MAX TEMP C.
4332        ?  N6 [X1],hyphen                    req=00 ex=4333                                     # MIN TEMP F.
4333        ?  N6 [X1],hyphen                    req=00 ex=4332                                     # MIN TEMP C.
7001        ?  N13                               req=01,02,8006,8026                                # NSN
7002        ?  X..30                             req=01,02                                          # MEAT CUT
7003        ?  N6,yymmdd N4,hhmi                 req=01,02,03                                       # EXPIRY TIME
7004        ?  N..4                              req=01+10,03+10                                    # ACTIVE POTENCY
7005        ?  X..12                             req=01,02                                          # CATCH AREA
7006        ?  N6,yymmdd                         req=01,02                                          # FIRST FREEZE DATE
7007        ?  N6,yymmdd [N6],yymmdd             req=01,02                                          # HARVEST DATE
7008        ?  X..3                              req=01,02                                          # AQUATIC SPECIES
7009        ?  X..10                             req=01,02                                          # FISHING GEAR TYPE
7010        ?  X..2                              req=01,02,03                                       # PROD METHOD
7011        ?  N6,yymmdd [N4],hhmi               req=01,02,03                                       # TEST BY DATE
7020        ?  X..20                             req=01+416,03+416,8006+416                         # REFURB LOT
7021        ?  X..20                             req=01,03,8006                                     # FUNC STAT
7022        ?  X..20                             req=01+7021,03+7021,8006+7021                      # REV STAT
7023        ?  X..30,gcppos1                                                                        # GIAI - ASSEMBLY
7030        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 0
7031        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 1
7032        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 2
7033        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 3
7034        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 4
7035        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 5
7036        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 6
7037        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 7
7038        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 8
7039        ?  N3,iso3166999 X..27               req=01,02                                          # PROCESSOR # 9
7040           N1 X1 X1 X1,importeridx                                                              # UIC+EXT
7041           X..4,packagetype                  req=00                                             # UFRGT UNIT TYPE
710         ?  X..20                             req=01                                             # NHRN PZN
711         ?  X..20                             req=01                                             # NHRN CIP
712         ?  X..20                             req=01                                             # NHRN CN
713         ?  X..20                             req=01                                             # NHRN DRN
714         ?  X..20                             req=01                                             # NHRN AIM
715         ?  X..20                             req=01                                             # NHRN NDC
716         ?  X..20                             req=01                                             # NHRN AIC
717         ?  X..20                             req=01                                             # NHRN SRN
7230        ?  X2 X..28                          req=01,8004                                        # CERT # 1
7231        ?  X2 X..28                          req=01,8004                                        # CERT # 2
7232        ?  X2 X..28                          req=01,8004                                        # CERT # 3
7233        ?  X2 X..28                          req=01,8004                                        # CERT # 4
7234        ?  X2 X..28                          req=01,8004                                        # CERT # 5
7235        ?  X2 X..28                          req=01,8004                                        # CERT # 6
7236        ?  X2 X..28                          req=01,8004                                        # CERT # 7
7237        ?  X2 X..28                          req=01,8004                                        # CERT # 8
7238        ?  X2 X..28                          req=01,8004                                        # CERT # 9
7239        ?  X2 X..28                          req=01,8004                                        # CERT # 10
7240        ?  X..20                             req=01,8006 ex=03                                  # PROTOCOL
7241        ?  N2,mediatype                      req=8017,8018                                      # AIDC MEDIA TYPE
7242        ?  X..25                             req=8017,8018                                      # VCN
7250        ?  N8,yyyymmdd                       req=8018 ex=7251                                   # DOB
7251        ?  N8,yyyymmdd N4,hhmi               req=8018 ex=7250                                   # DOB TIME
7252        ?  N1,iso5218                        req=8018                                           # BIO SEX
7253        ?  X..40,pcenc                       req=8017,8018 ex=7256,7259                         # FAMILY NAME
7254        ?  X..40,pcenc                       req=8017,8018 ex=7256,7259                         # GIVEN NAME
7255        ?  X..10                             req=8017,8018 ex=7256,7259                         # SUFFIX
7256        ?  X..90,pcenc                       req=8017,8018                                      # FULL NAME
7257        ?  X..70,pcenc                       req=8018                                           # PERSON ADDR
7258        ?  X3,posinseqslash                  req=8018+7259                                      # BIRTH SEQUENCE
7259        ?  X..40,pcenc                       req=8018 ex=7256                                   # BABY
8001        ?  N4,nonzero N5,nonzero N3,nonzero N1,winding N1  req=01                               # DIMENSIONS
8002        ?  X..20                                                                                # CMT No.
8003        ?  N1,zero N13,csum,gcppos1 [X..16]  dlpkey                                             # GRAI
8004        ?  X..30,gcppos1                     dlpkey=7040                                        # GIAI
8005        ?  N6                                req=01,02                                          # PRICE PER UNIT
8006        ?  N14,csum,gcppos2 N4,pieceoftotal  ex=01,03,37 dlpkey=22,10,21                        # ITIP
8007        ?  X..34,iban                        req=415                                            # IBAN
8008        ?  N6,yymmdd N2,hh [N2],mi [N2],ss   req=01,02,03                                       # PROD TIME
8009        ?  X..50                             req=00,01,03                                       # OPTSEN
8010        ?  Y..30,gcppos1                     dlpkey=8011                                        # CPID
8011           N..12,nozeroprefix                req=8010                                           # CPID SERIAL
8012        ?  X..20                             req=01,03,8006                                     # VERSION
8013        ?  X..25,csumalpha,gcppos1           dlpkey                                             # GMN
8014           X..25,csumalpha,gcppos1,hasnondigit  req=01                                          # MUDI
8017        ?  N18,csum,gcppos1                  ex=8018 dlpkey=8019                                # GSRN - PROVIDER
8018        ?  N18,csum,gcppos1                  ex=8017 dlpkey=8019                                # GSRN - RECIPIENT
8019           N..10                             req=8017,8018                                      # SRIN
8020           X..25                             req=415                                            # REF No.
8026        ?  N14,csum,gcppos2 N4,pieceoftotal  req=37 ex=02,03,8006                               # ITIP CONTENT
8030        ?  Z..90  req=00,01+21,03+21,253,255,8003,8004,8006+21,8010+8011,8017,8018              # DIGSIG
8040           N15                               req=01+21                                          # IMEI
8041           N15                               req=01+21+8040                                     # IMEI2
8042           N32                               req=01+21+8040                                     # ESIM
8043           N18 [N..2]                        req=01+21+8040                                     # PSIM
8110        ?  X..70,couponcode
8111        ?  N4                                req=255                                            # POINTS
8112        ?  X..70,couponposoffer
8200           X..70                             req=01                                             # PRODUCT URL
90          ?  X..30                                                                                # INTERNAL
91-99       ?  X..90                                                                                # INTERNAL
"""

// ---------------------------------------------------------------------------
// Lange Beschreibungen, Feld "description" aus
// https://ref.gs1.org/ai/GS1_Application_Identifiers.jsonld (Version 1.2, geaendert 2026-01-26)
// (GS1 AISBL, Apache 2.0). Zeilenformat: AI oder Bereich <TAB> Beschreibung.
// Wortlaut unveraendert.
// ---------------------------------------------------------------------------

internal const val GS1_AI_DESCRIPTIONS = """
00	Serial Shipping Container Code (SSCC)
01	Global Trade Item Number (GTIN)
02	Global Trade Item Number (GTIN) of contained trade items
03	Identification of a Made-to-Order (MtO) trade item (GTIN)
10	Batch or lot number
11	Production date (YYMMDD)
12	Due date (YYMMDD)
13	Packaging date (YYMMDD)
15	Best before date (YYMMDD)
16	Sell by date (YYMMDD)
17	Expiration date (YYMMDD)
20	Internal product variant
21	Serial number
22	Consumer product variant
235	Third Party Controlled, Serialised Extension of Global Trade Item Number (GTIN) (TPX)
240	Additional product identification assigned by the manufacturer
241	Customer part number
242	Made-to-Order variation number
243	Packaging component number
250	Secondary serial number
251	Reference to source entity
253	Global Document Type Identifier (GDTI)
254	Global Location Number (GLN) extension component
255	Global Coupon Number (GCN)
30	Variable count of items (variable measure trade item)
3100-3105	Net weight, kilograms (variable measure trade item)
3110-3115	Length or first dimension, metres (variable measure trade item)
3120-3125	Width, diameter, or second dimension, metres (variable measure trade item)
3130-3135	Depth, thickness, height, or third dimension, metres (variable measure trade item)
3140-3145	Area, square metres (variable measure trade item)
3150-3155	Net volume, litres (variable measure trade item)
3160-3165	Net volume, cubic metres (variable measure trade item)
3200-3205	Net weight, pounds (variable measure trade item)
3210-3215	Length or first dimension, inches (variable measure trade item)
3220-3225	Length or first dimension, feet (variable measure trade item)
3230-3235	Length or first dimension, yards (variable measure trade item)
3240-3245	Width, diameter, or second dimension, inches (variable measure trade item)
3250-3255	Width, diameter, or second dimension, feet (variable measure trade item)
3260-3265	Width, diameter, or second dimension, yards (variable measure trade item)
3270-3275	Depth, thickness, height, or third dimension, inches (variable measure trade item)
3280-3285	Depth, thickness, height, or third dimension, feet (variable measure trade item)
3290-3295	Depth, thickness, height, or third dimension, yards (variable measure trade item)
3300-3305	Logistic weight, kilograms
3310-3315	Length or first dimension, metres
3320-3325	Width, diameter, or second dimension, metres
3330-3335	Depth, thickness, height, or third dimension, metres
3340-3345	Area, square metres
3350-3355	Logistic volume, litres
3360-3365	Logistic volume, cubic metres
3370-3375	Kilograms per square metre
3400-3405	Logistic weight, pounds
3410-3415	Length or first dimension, inches
3420-3425	Length or first dimension, feet
3430-3435	Length or first dimension, yards
3440-3445	Width, diameter, or second dimension, inches
3450-3455	Width, diameter, or second dimension, feet
3460-3465	Width, diameter, or second dimension, yard
3470-3475	Depth, thickness, height, or third dimension, inches
3480-3485	Depth, thickness, height, or third dimension, feet
3490-3495	Depth, thickness, height, or third dimension, yards
3500-3505	Area, square inches (variable measure trade item)
3510-3515	Area, square feet (variable measure trade item)
3520-3525	Area, square yards (variable measure trade item)
3530-3535	Area, square inches
3540-3545	Area, square feet
3550-3555	Area, square yards
3560-3565	Net weight, troy ounces (variable measure trade item)
3570-3575	Net weight (or volume), ounces (variable measure trade item)
3600-3605	Net volume, quarts (variable measure trade item)
3610-3615	Net volume, gallons U.S. (variable measure trade item)
3620-3625	Logistic volume, quarts
3630-3635	Logistic volume, gallons U.S.
3640-3645	Net volume, cubic inches (variable measure trade item)
3650-3655	Net volume, cubic feet (variable measure trade item)
3660-3665	Net volume, cubic yards (variable measure trade item)
3670-3675	Logistic volume, cubic inches
3680-3685	Logistic volume, cubic feet
3690-3695	Logistic volume, cubic yards
37	Count of trade items or trade item pieces contained in a logistic unit
3900-3909	Applicable amount payable or Coupon value, local currency
3910-3919	Applicable amount payable with ISO currency code
3920-3929	Applicable amount payable, single monetary area (variable measure trade item)
3930-3939	Applicable amount payable with ISO currency code (variable measure trade item)
3940-3943	Percentage discount of a coupon
3950-3955	Amount Payable per unit of measure single monetary area (variable measure trade item)
400	Customers purchase order number
401	Global Identification Number for Consignment (GINC)
402	Global Shipment Identification Number (GSIN)
403	Routing code
410	Ship to / Deliver to Global Location Number (GLN)
411	Bill to / Invoice to Global Location Number (GLN)
412	Purchased from Global Location Number (GLN)
413	Ship for / Deliver for - Forward to Global Location Number (GLN)
414	Identification of a physical location - Global Location Number (GLN)
415	Global Location Number (GLN) of the invoicing party
416	Global Location Number (GLN) of the production or service location
417	Party Global Location Number (GLN)
420	Ship to / Deliver to postal code within a single postal authority
421	Ship to / Deliver to postal code with ISO country code
422	Country of origin of a trade item
423	Country of initial processing
424	Country of processing
425	Country of disassembly
426	Country covering full process chain
427	Country subdivision Of origin
4300	Ship-to / Deliver-to company name
4301	Ship-to / Deliver-to contact
4302	Ship-to / Deliver-to address line 1
4303	Ship-to / Deliver-to address line 2
4304	Ship-to / Deliver-to suburb
4305	Ship-to / Deliver-to locality
4306	Ship-to / Deliver-to region
4307	Ship-to / Deliver-to country code
4308	Ship-to / Deliver-to telephone number
4309	Ship-to / Deliver-to GEO location
4310	Return-to company name
4311	Return-to contact
4312	Return-to address line 1
4313	Return-to address line 2
4314	Return-to suburb
4315	Return-to locality
4316	Return-to region
4317	Return-to country code
4318	Return-to postal code
4319	Return-to telephone number
4320	Service code description
4321	Dangerous goods flag
4322	Authority to leave
4323	Signature required flag
4324	Not before delivery date time (YYMMDDhhmm)
4325	Not after delivery date time (YYMMDDhhmm)
4326	Release date (YYMMDD)
4330	Maximum temperature in Fahrenheit (expressed in hundredths of degrees)
4331	Maximum temperature in Celsius (expressed in hundredths of degrees)
4332	Minimum temperature in Fahrenheit (expressed in hundredths of degrees)
4333	Minimum temperature in Celsius (expressed in hundredths of degrees)
7001	NATO Stock Number (NSN)
7002	UN/ECE meat carcasses and cuts classification
7003	Expiration date and time (YYMMDDhhmm)
7004	Active potency
7005	Catch area
7006	First freeze date (YYMMDD)
7007	Harvest date (YYMMDD[YYMMDD])
7008	Species for fishery purposes
7009	Fishing gear type
7010	Production method
7011	Test by date (YYMMDD[hhmm])
7020	Refurbishment lot ID
7021	Functional status
7022	Revision status
7023	Global Individual Asset Identifier (GIAI) of an assembly
7030-7039	Number of processor with three-digit ISO country code
7040	GS1 UIC with Extension 1 and Importer index
7041	UN/CEFACT freight unit type
710	National Healthcare Reimbursement Number (NHRN) - Germany PZN
711	National Healthcare Reimbursement Number (NHRN) - France CIP
712	National Healthcare Reimbursement Number (NHRN) - Spain CN
713	National Healthcare Reimbursement Number (NHRN) - Brasil DRN
714	National Healthcare Reimbursement Number (NHRN) - Portugal AIM
715	National Healthcare Reimbursement Number (NHRN) - United States of America NDC
716	National Healthcare Reimbursement Number (NHRN) - Italy AIC
717	National Healthcare Reimbursement Number (NHRN) - Costa Rica Sanitary Register Number
7230-7239	Certification Reference
7240	Protocol ID
7241	AIDC media type
7242	Version Control Number (VCN)
7250	Date of birth (YYYYMMDD)
7251	Date and time of birth (YYYYMMDDhhmm)
7252	Biological sex
7253	Family name of person
7254	Given name of person
7255	Name suffix of person
7256	Full name of person
7257	Address of person
7258	Baby birth sequence
7259	Baby of family name
8001	Roll products (width, length, core diameter, direction, splices)
8002	Cellular mobile telephone identifier
8003	Global Returnable Asset Identifier (GRAI)
8004	Global Individual Asset Identifier (GIAI)
8005	Price per unit of measure
8006	Identification of an individual trade item piece (ITIP)
8007	International Bank Account Number (IBAN)
8008	Date and time of production (YYMMDDhh[mm[ss]])
8009	Optically Readable Sensor Indicator
8010	Component/Part Identifier (CPID)
8011	Component/Part Identifier serial number (CPID SERIAL)
8012	Software version
8013	Global Model Number (GMN)
8014	Highly Individualised Device Registration Identifier (HIDRI)
8017	Global Service Relation Number (GSRN) to identify the relationship between an organisation offering services and the provider of services
8018	Global Service Relation Number (GSRN) to identify the relationship between an organisation offering services and the recipient of services
8019	Service Relation Instance Number (SRIN)
8020	Payment slip reference number
8026	Identification of pieces of a trade item (ITIP) contained in a logistic unit
8030	Digital Signature (DigSig)
8040	Internatinal Mobile Equipment Identity (IMEI)
8041	Internatinal Mobile Equipment Identity 2 (IMEI2)
8042	Embedded SIM number
8043	Physical SIM number
8110	Coupon code identification for use in North America
8111	Loyalty points of a coupon
8112	Positive offer file coupon code identification for use in North America
8200	Extended Packaging URL
90	Information mutually agreed between trading partners
91-99	Company internal information
"""

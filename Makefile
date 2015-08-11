.PHONY: all build pack run testrun


all: build

build :
	sbt pack
	ln -s target/pack/bin/play_icfp2015 .

pack-tar:
	tar cvfz target/hayato.tar.gz Makefile src/main/scala/io/hayato/icfpc2015/Game.scala src/main/resources/logback.xml project/plugins.sbt build.sbt README play_icfp2015

run-sample:
	echo "This should results in a a score of 3,261"
	cd src/main/resources && ../../../target/pack/bin/play_icfp2015 -f problem_6.json -r problem_6_seed_0.json

run:
	cd src/main/resources && ../../../target/pack/bin/play_icfp2015 \
	-f problem_0.json  \
	-f problem_1.json  \
	-f problem_2.json  \
	-f problem_3.json  \
	-f problem_4.json  \
	-f problem_5.json  \
	-f problem_6.json  \
	-f problem_7.json  \
	-f problem_8.json  \
	-f problem_9.json  \
	-f problem_10.json  \
	-f problem_11.json  \
	-f problem_12.json  \
	-f problem_13.json  \
	-f problem_14.json  \
	-f problem_15.json  \
	-f problem_16.json  \
	-f problem_17.json  \
	-f problem_18.json  \
	-f problem_19.json  \
	-f problem_20.json  \
	-f problem_21.json  \
	-f problem_22.json  \
	-f problem_23.json  \
	-f problem_24.json  \
        -o output.json

run-fast:
	cd src/main/resources && ../../../target/pack/bin/play_icfp2015 \
	-f problem_0.json  \
	-f problem_1.json  \
	-f problem_2.json  \
	-f problem_3.json  \
	-f problem_4.json  \
	-f problem_5.json  \
	-f problem_6.json  \
	-f problem_7.json  \
	-f problem_8.json  \
	-f problem_9.json  \
	-f problem_10.json  \
	-f problem_11.json  \
	-f problem_12.json  \
	-f problem_13.json  \
	-f problem_14.json  \
	-f problem_15.json  \
	-f problem_16.json  \
	-f problem_17.json  \
	-f problem_18.json  \
	-f problem_19.json  \
	-f problem_20.json  \
	-f problem_21.json  \
	-f problem_22.json  \
	-f problem_23.json

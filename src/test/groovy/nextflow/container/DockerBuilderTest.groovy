/*
 * Copyright (c) 2013-2017, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2017, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.container
import java.nio.file.Paths

import nextflow.util.MemoryUnit
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DockerBuilderTest extends Specification {


    def 'test docker mounts'() {

        given:
        def builder = [:] as DockerBuilder
        def files =  [Paths.get('/folder/data'),  Paths.get('/folder/db'), Paths.get('/folder/db') ]
        def real = [ Paths.get('/user/yo/nextflow/bin'), Paths.get('/user/yo/nextflow/work'), Paths.get('/db/pdb/local/data') ]
        def quotes =  [ Paths.get('/folder with blanks/A'), Paths.get('/folder with blanks/B') ]

        expect:
        builder.makeVolumes([]).toString() == '-v "$PWD":"$PWD"'
        builder.makeVolumes(files).toString() == '-v /folder:/folder -v "$PWD":"$PWD"'
        builder.makeVolumes(real).toString()  == '-v /user/yo/nextflow:/user/yo/nextflow -v /db/pdb/local/data:/db/pdb/local/data -v "$PWD":"$PWD"'
        builder.makeVolumes(quotes).toString() == '-v /folder\\ with\\ blanks:/folder\\ with\\ blanks -v "$PWD":"$PWD"'

    }


    def 'test docker env'() {

        given:
        def builder = [:] as DockerBuilder

        expect:
        builder.makeEnv('X=1').toString() == '-e "X=1"'
        builder.makeEnv([VAR_X:1, VAR_Y: 2]).toString() == '-e "VAR_X=1" -e "VAR_Y=2"'
    }

    def 'test docker create command line'() {

        setup:
        def env = [FOO: 1, BAR: 'hello world']
        def db_file = Paths.get('/home/db')

        expect:
        new DockerBuilder('fedora')
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" fedora'

        new DockerBuilder('fedora')
                .addEnv(env)
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) -e "FOO=1" -e "BAR=hello world" -v "$PWD":"$PWD" -w "$PWD" fedora'

        new DockerBuilder('ubuntu')
                .params(temp:'/hola')
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) -v /hola:/tmp -v "$PWD":"$PWD" -w "$PWD" ubuntu'

        new DockerBuilder('busybox')
                .params(sudo: true)
                .build()
                .runCommand == 'sudo docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" busybox'

        new DockerBuilder('busybox')
                .params(entry: '/bin/bash')
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" --entrypoint /bin/bash busybox'

        new DockerBuilder('busybox')
                .params(runOptions: '-x --zeta')
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" -x --zeta busybox'

        new DockerBuilder('busybox')
                .params(userEmulation:true)
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) -e "HOME=${HOME}" -v /etc/passwd:/etc/passwd:ro -v /etc/shadow:/etc/shadow:ro -v /etc/group:/etc/group:ro -v $HOME:$HOME -v "$PWD":"$PWD" -w "$PWD" busybox'

        new DockerBuilder('busybox')
                .params(disableUserMapping: true)
                .build()
                .runCommand == 'docker run -i -v "$PWD":"$PWD" -w "$PWD" busybox'


        new DockerBuilder('busybox')
                .setName('hola')
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" --name hola busybox'

        new DockerBuilder('busybox')
                .params(engineOptions: '--tlsverify --tlscert="/path/to/my/cert"')
                .build()
                .runCommand == 'docker --tlsverify --tlscert="/path/to/my/cert" run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" busybox'

        new DockerBuilder('fedora')
                .addEnv(env)
                .addMount(db_file)
                .addMount(db_file)  // <-- add twice the same to prove that the final string won't contain duplicates
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) -e "FOO=1" -e "BAR=hello world" -v /home/db:/home/db -v "$PWD":"$PWD" -w "$PWD" fedora'

        new DockerBuilder('fedora')
                .params(readOnlyInputs: true)
                .addMount(db_file)
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) -v /home/db:/home/db:ro -v "$PWD":"$PWD" -w "$PWD" fedora'


    }

    def 'test memory and cpuset' () {

        expect:
        new DockerBuilder('fedora')
                .setCpus('1,2')
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) --cpuset-cpus 1,2 -v "$PWD":"$PWD" -w "$PWD" fedora'

        new DockerBuilder('fedora')
                .params(legacy:true)
                .setCpus('1,2')
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) --cpuset 1,2 -v "$PWD":"$PWD" -w "$PWD" fedora'

        new DockerBuilder('fedora')
                .setMemory('10g')
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) --memory 10g -v "$PWD":"$PWD" -w "$PWD" fedora'

        new DockerBuilder('fedora')
                .setMemory(new MemoryUnit('100M'))
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) --memory 100m -v "$PWD":"$PWD" -w "$PWD" fedora'

        new DockerBuilder('fedora')
                .setCpus('1-3')
                .setMemory(new MemoryUnit('100M'))
                .build()
                .runCommand == 'docker run -i -u $(id -u):$(id -g) --cpuset-cpus 1-3 --memory 100m -v "$PWD":"$PWD" -w "$PWD" fedora'

    }

    def 'test add mount'() {

        when:
        def docker = new DockerBuilder('fedora')
        docker.addMount(Paths.get('hello'))
        then:
        docker.mounts.size() == 1
        docker.mounts.contains(Paths.get('hello'))

        when:
        docker.addMount(null)
        then:
        docker.mounts.size() == 1

    }

    def 'test get commands'() {

        when:
        def docker =  new DockerBuilder('busybox').setName('c1').build()
        then:
        docker.runCommand == 'docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" --name c1 busybox'
        docker.removeCommand == 'docker rm c1'
        docker.killCommand == 'docker kill c1'

        when:
        docker =  new DockerBuilder('busybox').setName('c2').params(sudo: true, remove: true).build()
        then:
        docker.runCommand == 'sudo docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" --name c2 busybox'
        docker.removeCommand == 'sudo docker rm c2'
        docker.killCommand == 'sudo docker kill c2'


        when:
        docker =  new DockerBuilder('busybox').setName('c3').params(remove: true).build()
        then:
        docker.runCommand == 'docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" --name c3 busybox'
        docker.removeCommand == 'docker rm c3'
        docker.killCommand == 'docker kill c3'

        when:
        docker =  new DockerBuilder('busybox').setName('c4').params(kill: 'SIGKILL').build()
        then:
        docker.killCommand == 'docker kill -s SIGKILL c4'

        when:
        docker =  new DockerBuilder('busybox').setName('c5').params(kill: false,remove: false).build()
        then:
        docker.killCommand == null
        docker.removeCommand == null

    }

    def 'should add docker run to shell script' () {

        when:
        def script = '''
            #!/bin/bash
            FOO=bar
            busybox --foo --bar
            do_this
            do_that
            '''
        def tokens = ContainerScriptTokens.parse(script)
        def docker = new DockerBuilder('busybox').addEnv(tokens.variables)
        docker.build()

        then:
        docker.addContainerRunCommand(tokens) == '''
            #!/bin/bash
            FOO=bar
            docker run -i -u $(id -u):$(id -g) -e "FOO=bar" -v "$PWD":"$PWD" -w "$PWD" busybox --foo --bar
            do_this
            do_that
            '''
                .stripIndent().leftTrim()

        when:
        tokens = ContainerScriptTokens.parse('#!/bin/bash\nbusybox')
        docker = new DockerBuilder('busybox')
        docker.build()
        then:
        docker.addContainerRunCommand(tokens) == '''
            #!/bin/bash
            docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" busybox
            '''
                .stripIndent().leftTrim()


    }


    def 'should get run command line' () {

        when:
        def cli = new DockerBuilder('ubuntu:14').build().getRunCommand()
        then:
        cli ==  'docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" ubuntu:14'

        when:
        cli = new DockerBuilder('ubuntu:14').build().getRunCommand('bwa --this --that file.fasta')
        then:
        cli ==  'docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" ubuntu:14 bwa --this --that file.fasta'

        when:
        cli = new DockerBuilder('ubuntu:14').params(entry:'/bin/bash').build().getRunCommand('bwa --this --that file.fasta')
        then:
        cli ==  'docker run -i -u $(id -u):$(id -g) -v "$PWD":"$PWD" -w "$PWD" --entrypoint /bin/bash ubuntu:14 -c "bwa --this --that file.fasta"'

    }


}

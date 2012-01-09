/**
 * Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.actor

import akka.testkit.AkkaSpec
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import akka.routing._
import akka.util.duration._

object DeployerSpec {
  val deployerConf = ConfigFactory.parseString("""
      akka.actor.deployment {
        /service1 {
        }
        /service3 {
          create-as {
            class = "akka.actor.DeployerSpec$RecipeActor"
          }
        }
        /service-direct {
          router = from-code
        }
        /service-direct2 {
          router = from-code
          # nr-of-instances ignored when router = direct
          nr-of-instances = 2
        }
        /service-round-robin {
          router = round-robin
        }
        /service-random {
          router = random
        }
        /service-scatter-gather {
          router = scatter-gather
          within = 2 seconds
        }
      }
      """, ConfigParseOptions.defaults)

  class RecipeActor extends Actor {
    def receive = { case _ ⇒ }
  }

}

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class DeployerSpec extends AkkaSpec(DeployerSpec.deployerConf) {

  "A Deployer" must {

    "be able to parse 'akka.actor.deployment._' with all default values" in {
      val service = "/service1"
      val deployment = system.asInstanceOf[ActorSystemImpl].provider.deployer.lookup(service)
      deployment must be('defined)

      deployment must be(Some(
        Deploy(
          service,
          deployment.get.config,
          None,
          NoRouter,
          LocalScope)))
    }

    "use None deployment for undefined service" in {
      val service = "/undefined"
      val deployment = system.asInstanceOf[ActorSystemImpl].provider.deployer.lookup(service)
      deployment must be(None)
    }

    "be able to parse 'akka.actor.deployment._' with recipe" in {
      val service = "/service3"
      val deployment = system.asInstanceOf[ActorSystemImpl].provider.deployer.lookup(service)
      deployment must be('defined)

      deployment must be(Some(
        Deploy(
          service,
          deployment.get.config,
          Some(ActorRecipe(classOf[DeployerSpec.RecipeActor])),
          NoRouter,
          LocalScope)))
    }

    "detect invalid number-of-instances" in {
      intercept[com.typesafe.config.ConfigException.WrongType] {
        val invalidDeployerConf = ConfigFactory.parseString("""
            akka.actor.deployment {
              /service-invalid-number-of-instances {
                router = round-robin
                nr-of-instances = boom
              }
            }
            """, ConfigParseOptions.defaults).withFallback(AkkaSpec.testConf)

        ActorSystem("invalid", invalidDeployerConf).shutdown()
      }
    }

    "be able to parse 'akka.actor.deployment._' with direct router" in {
      assertRouting(NoRouter, "/service-direct")
    }

    "ignore nr-of-instances with direct router" in {
      assertRouting(NoRouter, "/service-direct2")
    }

    "be able to parse 'akka.actor.deployment._' with round-robin router" in {
      assertRouting(RoundRobinRouter(1), "/service-round-robin")
    }

    "be able to parse 'akka.actor.deployment._' with random router" in {
      assertRouting(RandomRouter(1), "/service-random")
    }

    "be able to parse 'akka.actor.deployment._' with scatter-gather router" in {
      assertRouting(ScatterGatherFirstCompletedRouter(nrOfInstances = 1, within = 2 seconds), "/service-scatter-gather")
    }

    def assertRouting(expected: RouterConfig, service: String) {
      val deployment = system.asInstanceOf[ActorSystemImpl].provider.deployer.lookup(service)
      deployment must be('defined)

      deployment must be(Some(
        Deploy(
          service,
          deployment.get.config,
          None,
          expected,
          LocalScope)))

    }

  }
}
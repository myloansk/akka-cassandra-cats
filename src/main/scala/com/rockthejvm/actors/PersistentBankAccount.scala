package com.rockthejvm.actors

import akka.actor.TypedActor.context
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import io.circe.Decoder.state

import java.util.UUID

class PersistentBankAccount   {

  sealed trait Command
  sealed trait Response

  case class CreateBankAccount(user: String, currency: String, initialBalance:Double, replyTo: ActorRef[Response])
  case class UpdateBalance(id: String, currency:String, amount: Double, replyTo: ActorRef[Response] )
  case class GetBankAccount(id: String, replyTo: ActorRef[Response])

  // events to persist to Cassandra
  trait Event
  case class BankAccountCreated(bankAccount: BankAccount) extends Event
  case class BalanceUpdated(amount: Double) extends Event


  // state
  case class BankAccount(id: String, user: String, currency: String, balance: Double)

  //Responses
  case class BankAccountCreatedResponse(id: String) extends Response
  case class BankAccountBalanceUpdatedResponse(maybeBankAccount:Option[BankAccount]) extends Response
  case class GetBankAccountResponse(maybeBankAcount: Option[BankAccount]) extends Response

  // command handler
  // event handler
  // state
  val commandHandler: (BankAccount, Command) => Effect[Event, BankAccount] = (state, command) =>
    command match {
      case CreateBankAccount(user, currency, initialBalance, bank)=>
        val id = state.id
        Effect
        .persist(BankAccountCreated(BankAccount(id, user , currency, balance )))
        .thenReply(bank)(_ => BankAccountCreatedResponse(id))

    }
    { case UpdateBalance(_, _, amount, replyTo) =>
      val newBalance = state.balance + amount
      if(newBalance < 0)
        Effect.reply(bank)(BankAccountBalanceUpdatedResponse(Failure(new RuntimeException("Can't withdraw more than available"))))
      else
        Effect
          .persist(BalanceUpdated(amount))
          .thenReply(bank)(newState => BankAccountBalanceUpdatedResponse(Success(newState)))
    case GetBankAccount(_, bank) =>
      Effect.reply(bank)(GetBankAccountResponse(Some(state)))
    }

  val eventHandler: (BankAccount, Event) => BankAccount = (state, event) =>
    event match {
      case BankAccountCreated(bankAccount) =>
        bankAccount case BalanceUpdated(amount) => state.copy(balance = state.balance + amount)
    }

  def apply(id: String) => Behavior[Command] =
    EventSourcedBehavior[Command, Event, BankAccount](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = BankAccount(id, "", "", 0.0), //
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )




}

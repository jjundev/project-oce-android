package com.jjundev.oneclickeng.learning.dialoguelearning.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.jjundev.oneclickeng.learning.dialoguelearning.model.ScriptTurn;
import com.jjundev.oneclickeng.learning.dialoguelearning.parser.DialogueScriptParser;
import org.junit.Test;

public class ScriptFlowControllerStreamingTest {

  @Test
  public void moveToNextTurn_waitsUntilStreamProvidesTurn() {
    ScriptFlowController controller = new ScriptFlowController(new DialogueScriptParser());
    controller.startStreaming("인사", "Coach", "Partner", "female");

    ScriptFlowController.NextTurnResult waiting = controller.moveToNextTurn();
    assertEquals(ScriptFlowController.NextTurnResult.Type.WAITING, waiting.getType());

    controller.appendStreamTurn(new ScriptTurn("안녕하세요", "Hello", "model"));
    ScriptFlowController.NextTurnResult firstTurn = controller.moveToNextTurn();
    assertEquals(ScriptFlowController.NextTurnResult.Type.TURN, firstTurn.getType());
    assertEquals(1, firstTurn.getCurrentStep());
    assertEquals(1, firstTurn.getTotalSteps());
    assertNotNull(firstTurn.getTurn());
  }

  @Test
  public void moveToNextTurn_finishesAfterStreamCompletion() {
    ScriptFlowController controller = new ScriptFlowController(new DialogueScriptParser());
    controller.startStreaming("인사", "Coach", "Partner", "female");
    controller.appendStreamTurn(new ScriptTurn("안녕하세요", "Hello", "model"));
    controller.appendStreamTurn(new ScriptTurn("반가워요", "Nice to meet you", "user"));

    ScriptFlowController.NextTurnResult first = controller.moveToNextTurn();
    assertEquals(ScriptFlowController.NextTurnResult.Type.TURN, first.getType());
    ScriptFlowController.NextTurnResult second = controller.moveToNextTurn();
    assertEquals(ScriptFlowController.NextTurnResult.Type.TURN, second.getType());

    ScriptFlowController.NextTurnResult waiting = controller.moveToNextTurn();
    assertEquals(ScriptFlowController.NextTurnResult.Type.WAITING, waiting.getType());

    controller.markStreamCompleted();
    ScriptFlowController.NextTurnResult finished = controller.moveToNextTurn();
    assertEquals(ScriptFlowController.NextTurnResult.Type.FINISHED, finished.getType());
    assertEquals(2, finished.getCurrentStep());
    assertEquals(2, finished.getTotalSteps());
  }
}

package com.example.rompeladrillos

import android.media.MediaPlayer
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Looper
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private var audioThread: AudioThread? = null

    private lateinit var scoreText: TextView
    private lateinit var paddle: View
    private lateinit var ball: View
    private lateinit var brickContainer: LinearLayout
    private lateinit var victoryText: TextView

    private var ballX = 0f
    private var ballY = 0f
    private var ballSpeedX = 3f
    private var ballSpeedY = -3f
    private var paddleX = 0f
    private var score = 0
    private val brickRows = 9
    private val brickColumns = 10
    private val brickWidth = 100
    private val brickHeight = 40
    private val brickMargin = 4
    private var isBallLaunched = false
    private var lives = 3

    private val handler = Handler(Looper.getMainLooper())
    private var gameRunnable: Runnable? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicia el hilo en segundo plano para reproducir el audio
        audioThread = AudioThread()
        audioThread?.start()

        scoreText = findViewById(R.id.scoreText)
        paddle = findViewById(R.id.paddle)
        ball = findViewById(R.id.ball)
        brickContainer = findViewById(R.id.brickContainer)
        victoryText = findViewById(R.id.victoryText)

        val newgame = findViewById<Button>(R.id.newgame)
        newgame.setOnClickListener {
            resetGame()
            startGame()
            newgame.visibility = View.INVISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Reanuda el audio si estaba pausado
        audioThread?.resumeAudio()
    }

    override fun onPause() {
        super.onPause()
        // Pausa el audio cuando la actividad no está en primer plano
        audioThread?.pauseAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detiene el hilo cuando la actividad se destruye
        audioThread?.stopAudio()
    }

    // Clase para el hilo en segundo plano que reproduce el audio
    inner class AudioThread : Thread() {
        private var mediaPlayer: MediaPlayer? = null
        @Volatile private var isPaused = false

        override fun run() {
            // Inicializa el MediaPlayer con la pista de audio deseada
            mediaPlayer = MediaPlayer.create(applicationContext, R.raw.goldentxk)

            // Configura el MediaPlayer para reproducir la pista de audio de manera indefinida
            mediaPlayer?.isLooping = true

            mediaPlayer?.start()
            while (!Thread.currentThread().isInterrupted) {
                synchronized(this) {
                    while (isPaused) {
                        (this as java.lang.Object).wait()
                    }
                }
                mediaPlayer?.let {
                    if (!it.isPlaying) {
                        it.start()
                    }
                }
            }
        }

        // Método para pausar la reproducción de audio
        @Synchronized
        fun pauseAudio() {
            mediaPlayer?.pause()
            isPaused = true
        }

        // Método para reanudar la reproducción de audio
        @Synchronized
        fun resumeAudio() {
            isPaused = false
            (this as java.lang.Object).notify()
        }

        // Método para detener la reproducción de audio y liberar recursos
        fun stopAudio() {
            mediaPlayer?.release()
            mediaPlayer = null
            interrupt()
        }
    }

    private fun initializeBricks() {
        val brickWidthWithMargin = (brickWidth + brickMargin).toInt()

        for (row in 0 until brickRows) {
            val rowLayout = LinearLayout(this)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowLayout.layoutParams = params

            for (col in 0 until brickColumns) {
                val brick = View(this)
                val brickParams = LinearLayout.LayoutParams(brickWidth, brickHeight)
                brickParams.setMargins(brickMargin, brickMargin, brickMargin, brickMargin)
                brick.layoutParams = brickParams
                brick.setBackgroundResource(R.drawable.ic_launcher_background)
                rowLayout.addView(brick)
            }
            brickContainer.addView(rowLayout)
        }
    }

    private fun moveBall() {
        ballX += ballSpeedX
        ballY += ballSpeedY

        ball.x = ballX
        ball.y = ballY
    }

    private fun movePaddle(x: Float) {
        paddleX = x - paddle.width / 2
        paddle.x = paddleX
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun checkCollision() {
        // Check collision with walls
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()

        if (ballX <= 0 || ballX + ball.width >= screenWidth) {
            ballSpeedX *= -1
        }

        if (ballY <= 0) {
            ballSpeedY *= -1
        }

        // Check collision with paddle
        if (ballY + ball.height >= paddle.y && ballY + ball.height <= paddle.y + paddle.height
            && ballX + ball.width >= paddle.x && ballX <= paddle.x + paddle.width
        ) {
            ballSpeedY *= -1
            score++
            scoreText.text = "Score: $score"
        }

        // Check collision with bricks
        var allBricksInvisible = true
        for (row in 0 until brickRows) {
            val rowLayout = brickContainer.getChildAt(row) as LinearLayout

            val rowTop = rowLayout.y + brickContainer.y
            val rowBottom = rowTop + rowLayout.height

            for (col in 0 until brickColumns) {
                val brick = rowLayout.getChildAt(col) as View

                if (brick.visibility == View.VISIBLE) {
                    allBricksInvisible = false
                    val brickLeft = brick.x + rowLayout.x
                    val brickRight = brickLeft + brick.width
                    val brickTop = brick.y + rowTop
                    val brickBottom = brickTop + brick.height

                    if (ballX + ball.width >= brickLeft && ballX <= brickRight
                        && ballY + ball.height >= brickTop && ballY <= brickBottom
                    ) {
                        brick.visibility = View.INVISIBLE
                        ballSpeedY *= -1
                        score++
                        scoreText.text = "Score: $score"
                        return  // Exit the function after finding a collision with a brick
                    }
                }
            }
        }

        // Check if all bricks are invisible
        if (allBricksInvisible) {
            showVictory()
        }

        // Check collision with bottom wall (paddle misses the ball)
        if (ballY + ball.height >= screenHeight) {
            // Reduce the number of lives
            lives--

            if (lives > 0 ) {
                Toast.makeText(this, "$lives balls left ", Toast.LENGTH_SHORT).show()
                // Reset the ball to its initial position
                resetBallPosition()
            } else {
                // Game over condition: No more lives left
                gameOver()
            }
        }
    }

    private fun resetBallPosition() {
        // Reset the ball to its initial position
        val displayMetrics = resources.displayMetrics
        val screenDensity = displayMetrics.density

        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        ballX = screenWidth / 2 - ball.width / 2
        ballY = screenHeight / 2 - ball.height / 2 + 525

        ball.x = ballX
        ball.y = ballY

        // Reset the ball's speed to constant values
        ballSpeedX = 3 * screenDensity
        ballSpeedY = -3 * screenDensity

        paddleX = screenWidth / 2 - paddle.width / 2
        paddle.x = paddleX

        // Implement any additional logic you need, such as reducing lives or showing a message
        // when the ball goes past the paddle.
    }

    private fun gameOver() {
        // Display a game over message or perform other actions
        scoreText.text = "Game Over"
        score = 0
        val newgame = findViewById<Button>(R.id.newgame)
        newgame.visibility = View.VISIBLE

        // Stop the game loop
        handler.removeCallbacks(gameRunnable!!)
    }

    private fun showVictory() {
        victoryText.visibility = View.VISIBLE
        victoryText.text = "You Win!"

        // Detener la pelota y la raqueta
        ballSpeedX = 0f
        ballSpeedY = 0f
        handler.removeCallbacks(gameRunnable!!)

        val newgame = findViewById<Button>(R.id.newgame)
        newgame.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun movepaddle() {
        paddle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    movePaddle(event.rawX)
                }
            }
            true
        }
    }

    private fun startGame() {
        movepaddle()
        val displayMetrics = resources.displayMetrics
        val screenDensity = displayMetrics.density

        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        paddleX = screenWidth / 2 - paddle.width / 2
        paddle.x = paddleX

        ballX = screenWidth / 2 - ball.width / 2
        ballY = screenHeight / 2 - ball.height / 2

        ballSpeedX = 3 * screenDensity
        ballSpeedY = -3 * screenDensity

        gameRunnable = object : Runnable {
            override fun run() {
                moveBall()
                checkCollision()
                handler.postDelayed(this, 10) // Delay 10 milliseconds before running again
            }
        }
        handler.post(gameRunnable!!)
    }

    private fun resetGame() {
        lives = 3
        score = 0
        scoreText.text = "Score: $score"
        victoryText.visibility = View.INVISIBLE

        // Clear the brick container to avoid duplication
        brickContainer.removeAllViews()
        initializeBricks()
    }
}

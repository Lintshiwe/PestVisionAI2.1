const path = require('path')
const express = require('express')
const morgan = require('morgan')
const http = require('http')
const https = require('https')

const app = express()
const port = process.env.PORT ? Number(process.env.PORT) : 3000
const streamUrl = process.env.STREAM_URL || 'http://localhost:8000/video/feed'
const backendBaseUrl = process.env.BACKEND_BASE_URL || 'http://localhost:8082'
const streamTarget = new URL(streamUrl)
const backendTarget = new URL(backendBaseUrl)

function buildRequestOptions(target, overrides = {}) {
  return {
    protocol: target.protocol,
    hostname: target.hostname,
    port: target.port || (target.protocol === 'https:' ? 443 : 80),
    path: `${target.pathname}${target.search}`,
    method: 'GET',
    ...overrides,
  }
}

function chooseClient(target) {
  return target.protocol === 'https:' ? https : http
}

function proxyJson(res, targetPath) {
  const targetUrl = new URL(targetPath, backendTarget)
  const client = chooseClient(targetUrl)
  const upstreamRequest = client.request(
    buildRequestOptions(targetUrl, {
      headers: {
        accept: 'application/json',
      },
    }),
    (upstreamResponse) => {
      const chunks = []
      upstreamResponse.on('data', (chunk) => chunks.push(chunk))
      upstreamResponse.on('end', () => {
        if (!res.headersSent) {
          res.status(upstreamResponse.statusCode || 502)
          const contentType =
            upstreamResponse.headers['content-type'] || 'application/json'
          res.set('content-type', contentType)
        }
        res.send(Buffer.concat(chunks))
      })
    }
  )

  upstreamRequest.on('error', (err) => {
    console.error('Backend JSON proxy error:', err.message)
    if (!res.headersSent) {
      res.status(502).json({ message: 'Backend unavailable' })
    } else {
      res.end()
    }
  })

  upstreamRequest.end()
}

app.use(morgan('dev'))
app.use(express.static(path.join(__dirname, 'public')))

app.get('/config.json', (_req, res) => {
  res.json({
    streamUrl: '/stream.mjpg',
    sourceStreamUrl: streamUrl,
    backendBaseUrl,
  })
})

app.get('/api/detections/recent', (_req, res) => {
  proxyJson(res, '/api/detections/recent')
})

app.get('/api/detections/sprays/recent', (_req, res) => {
  proxyJson(res, '/api/detections/sprays/recent')
})

app.get('/reports/detections.xlsx', (req, res) => {
  const targetUrl = new URL('/api/reports/detections.xlsx', backendTarget)
  const client = chooseClient(targetUrl)
  const upstreamRequest = client.request(
    buildRequestOptions(targetUrl, {
      headers: {
        accept:
          'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      },
    }),
    (upstreamResponse) => {
      if (!res.headersSent) {
        res.writeHead(upstreamResponse.statusCode || 502, {
          ...upstreamResponse.headers,
          'cache-control': 'no-store',
        })
      }
      upstreamResponse.pipe(res)
    }
  )

  upstreamRequest.on('error', (err) => {
    console.error('Report proxy error:', err.message)
    if (!res.headersSent) {
      res.status(502).send('Report unavailable')
    } else {
      res.end()
    }
  })

  req.on('close', () => upstreamRequest.destroy())

  upstreamRequest.end()
})

app.get('/events', (req, res) => {
  const targetUrl = new URL('/api/detections/stream', backendTarget)
  const client = chooseClient(targetUrl)

  res.writeHead(200, {
    'content-type': 'text/event-stream',
    'cache-control': 'no-cache',
    connection: 'keep-alive',
    'x-accel-buffering': 'no',
  })

  res.write(': connected\n\n')

  const upstreamRequest = client.request(
    buildRequestOptions(targetUrl, {
      headers: {
        accept: 'text/event-stream',
      },
    }),
    (upstreamResponse) => {
      upstreamResponse.on('data', (chunk) => {
        res.write(chunk)
      })
      upstreamResponse.on('end', () => {
        res.write('event: end\ndata: {}\n\n')
        res.end()
      })
    }
  )

  upstreamRequest.on('error', (err) => {
    console.error('SSE proxy error:', err.message)
    res.write(
      `event: error\ndata: ${JSON.stringify({
        message: 'SSE proxy error',
      })}\n\n`
    )
    res.end()
  })

  req.on('close', () => {
    upstreamRequest.destroy()
    res.end()
  })

  upstreamRequest.end()
})

app.get('/stream.mjpg', (req, res) => {
  const client = streamTarget.protocol === 'https:' ? https : http

  const upstreamRequest = client.request(
    {
      protocol: streamTarget.protocol,
      hostname: streamTarget.hostname,
      port:
        streamTarget.port || (streamTarget.protocol === 'https:' ? 443 : 80),
      path: `${streamTarget.pathname}${streamTarget.search}`,
      method: 'GET',
      headers: {
        ...req.headers,
        host: streamTarget.host,
      },
    },
    (upstreamResponse) => {
      const status = upstreamResponse.statusCode || 200
      if (!res.headersSent) {
        res.writeHead(status, {
          ...upstreamResponse.headers,
          'cache-control': 'no-store',
        })
      }
      upstreamResponse.pipe(res)
    }
  )

  upstreamRequest.on('error', (err) => {
    console.error('Stream proxy error:', err.message)
    if (!res.headersSent) {
      res.status(502).send('Stream unavailable')
    } else {
      res.end()
    }
  })

  req.on('close', () => {
    upstreamRequest.destroy()
  })

  upstreamRequest.end()
})

app.listen(port, () => {
  console.log(`Live feed frontend listening on http://localhost:${port}`)
})
